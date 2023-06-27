package com.example.falldetection;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class DataReceivingService extends Service {

    private DataReceivingBinder serviceBinder = new DataReceivingBinder();
    private NotificationManager notificationManager;
    private DataClient dataClient;

    private Socket m_Socket = null;
    private static final int SERVER_PORT = 7896;
//    private static final int SERVER_PORT = 3389;
    public MutableLiveData<Boolean> isConnectedToServer = new MutableLiveData<>(false);
    public MutableLiveData<ServerStatus> serverStatus = new MutableLiveData<>(ServerStatus.DISCONNECTED);

    private static final String CHANNEL_ID = "100";
    private static final int NOTIFICATION_ID = 001;
    private SharedPreferences sharedPreferences;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("Alex", "Service created");

        dataClient = Wearable.getDataClient(this);

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Fall Detection";
            String description = "Shows that fall detection is active";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviours after this.
            notificationManager.createNotificationChannel(channel);
        }
//        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
//        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
//                "FallDetection:FallDetectionWakeLock");
        sharedPreferences = getApplication().getSharedPreferences("ContactPrefs", Context.MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Alex", "Service onStartCommand");

        String ipAddress = intent.getExtras().getString("ipAddress");

        // starts foreground service with notification
        Intent intentNotification = new Intent(DataReceivingService.this, MainActivity.class);

        intentNotification.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Fall Detection Service Running")
                .setContentText("Detecting falls")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        startForeground(NOTIFICATION_ID, mBuilder.build());
        startReceiving(ipAddress);

        return super.onStartCommand(intent, flags, startId);
    }

    public void startReceiving(String ipAddress) {
        connectToServer(ipAddress);
        dataClient.addListener(accelerometerDataChangedListener);
        serverStatus.postValue(ServerStatus.CONNECTING);
    }

    public void stopDataReceivingService() {
        stopForeground(true);
        Log.d("Alex", "stopFallDetectionService called");
        stopReceiving();
        stopSelf();
    }

    private void stopReceiving() {
        disconnectFromServer();
        dataClient.removeListener(accelerometerDataChangedListener);
        isConnectedToServer.postValue(false);
    }

    public void connectToServer(String ipAddress) {
        new Thread(new Runnable() {
            Handler handler = new Handler(Looper.getMainLooper());
            @Override
            public void run() {
                try {
                    Log.d("Alex", "Attempting to connect to server " + ipAddress + ":" + SERVER_PORT);
                    m_Socket = new Socket(ipAddress, SERVER_PORT);
                    m_Socket.setSoTimeout(5000); // timeout of 5 seconds
                    handler.post(() -> {
//                        textView.setText("Connected to server");
                        isConnectedToServer.postValue(true);
                        serverStatus.postValue(ServerStatus.CONNECTED);
                    });
                } catch (IOException e) {
                    Log.d("Alex", "Failed to connect to server", e);
                    stopDataReceivingService();
                    handler.post(() -> {
                        isConnectedToServer.postValue(false);
                        serverStatus.postValue(ServerStatus.DISCONNECTED);
                    });
                }
            }
        }).start();
    }

    public void disconnectFromServer() {
        Handler handler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(m_Socket != null)
                        m_Socket.close();
                    handler.post(() -> {
                        isConnectedToServer.postValue(false);
                        serverStatus.postValue(ServerStatus.DISCONNECTED);
//                        textView.setText("Disconnected from server");
                    });
                } catch (IOException e) {
                    Log.d("Alex", "Failed to disconnect from server", e);
                    stopDataReceivingService();
                }
            }
        }).start();
    }

    public void sendFallWarningToWatch() {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/fall_detected");
        putDataMapRequest.getDataMap().putBoolean("fall", true);
        putDataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());
//        putDataMapRequest.getDataMap().putBoolean("fall", true);
        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        dataClient.putDataItem(request)
                .addOnSuccessListener(new OnSuccessListener<DataItem>() {
                    @Override
                    public void onSuccess(DataItem dataItem) {
                        Log.d("Alex", "Fall detected info sent successfully hasFallen: " + true);
//                        hasFallen = !hasFallen;
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.d("Alex", "Failed to send fall detected info", e);
                    }
                });
    }

    public void makePhoneCall() {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
//        callIntent.setData(Uri.parse("tel:+447554045053"));
//        String phoneNumber = "tel:" + mainViewModel.getContactNumber();
        String phoneNumber = sharedPreferences.getString("contact_number", "");
//        callIntent.setData(Uri.parse("tel:+447500544628"));
        callIntent.setData(Uri.parse(phoneNumber));
        startActivity(callIntent);
    }

    public void sendSMS() {
//        String phoneNumber = mainViewModel.getContactNumber();
        String phoneNumber = sharedPreferences.getString("contact_number", "");
        String message = "Help! I have fallen down!";
        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
        Toast.makeText(this, "SMS Sent!", Toast.LENGTH_SHORT).show();
    }

    DataClient.OnDataChangedListener accelerometerDataChangedListener = new DataClient.OnDataChangedListener() {
        @Override
        public void onDataChanged(@NonNull DataEventBuffer dataEventBuffer) {
            for(DataEvent event : dataEventBuffer) {
                if(event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = event.getDataItem();
                    if(dataItem.getUri().getPath().equals("/accelerometer_json")) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        String accelerometerJson = dataMap.getString("json_object");
                        Log.d("Alex", accelerometerJson);
                        Toast.makeText(DataReceivingService.this, "Received JSON object", Toast.LENGTH_SHORT).show();
//                        Toast.makeText(MainActivity.this, accelerometerJson, Toast.LENGTH_SHORT).show();
//                        sendJsonWithTCP(accelerometerJson, editTextIPAddress.getText().toString());
                        if(isConnectedToServer.getValue())
                            sendJsonToServer(accelerometerJson);
                    }
                    else if(dataItem.getUri().getPath().equals("/fall_confirmation")) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        boolean fallConfirmation = dataMap.getBoolean("fall");
                        if(fallConfirmation) {
                            sendSMS();
                        }
                    }
                }
            }
        }
    };

    private void sendJsonToServer(String jsonString) {
//        Handler handler = new Handler(Looper.getMainLooper());
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//
//            }
//        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream out = m_Socket.getOutputStream();
                    out.write(jsonString.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Log.d("Alex", "Sent JSON to server!");

                    BufferedReader in = new BufferedReader(new InputStreamReader(m_Socket.getInputStream()));
//                    DataInputStream in = new DataInputStream(m_Socket.getInputStream());

                    try {
                        String response = in.readLine();  // read the acknowledgement from the server
//                        String response = in.readUTF();  // read the acknowledgement from the server
                        Log.d("Alex", "Received acknowledgement: " + response);
                        if(response.equals("fall"))
                            sendFallWarningToWatch();
                    } catch (SocketTimeoutException e) {
                        Log.d("Alex", "No acknowledgement received.");
                    }
                } catch (IOException e) {
                    Log.d("Alex", "Failed to send JSON", e);
                }
            }
        }).start();
    }

    class DataReceivingBinder extends Binder {
        public DataReceivingService getInstance() {
            return DataReceivingService.this;
        }
    }

    public enum ServerStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}
