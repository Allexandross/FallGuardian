package com.example.falldetection;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventCallback;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class PhoneSensorService extends Service {

    public static final int ACCELEROMETER_SAMPLING_RATE = 100;
    public static final int SECONDS_TO_SEND_DATA = 9;

    private PhoneSensorBinder serviceBinder = new PhoneSensorBinder();
    private NotificationManager notificationManager;

    private Socket m_Socket = null;
    private static final int SERVER_PORT = 7896;
    //    private static final int SERVER_PORT = 3389;
    public boolean connectedToServer = false;
    public MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);

    private static final String CHANNEL_ID = "100";
    private static final int NOTIFICATION_ID = 001;
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private Sensor gyroscopeSensor;

    private JSONObject jsonObject = new JSONObject();
    private JSONArray jsonAccXArray = new JSONArray();
    private JSONArray jsonAccYArray = new JSONArray();
    private JSONArray jsonAccZArray = new JSONArray();
    private JSONArray jsonGyroXArray = new JSONArray();
    private JSONArray jsonGyroYArray = new JSONArray();
    private JSONArray jsonGyroZArray = new JSONArray();
    private JSONArray jsonTimestampArray = new JSONArray();


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    private SensorEventCallback accelerometerEventCallback = new SensorEventCallback() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            super.onSensorChanged(event);
            if (jsonAccXArray.length() == ACCELEROMETER_SAMPLING_RATE * SECONDS_TO_SEND_DATA) {
                Log.d("Alex","Acc array full");
                Log.d("Alex", "gyro array length " + jsonGyroXArray.length());
                if (jsonGyroXArray.length() == ACCELEROMETER_SAMPLING_RATE * SECONDS_TO_SEND_DATA) {
                    Log.d("Alex","Gyro array also full");
                    createJSONObject();
                    if (connectedToServer) {
                        sendJsonToServer(jsonObject.toString());
                    }
                    clearJSONArrays();
                }
                return;
            }
            ArrayList<Float> tuple = new ArrayList();
            tuple.add(event.values[0]);
            tuple.add(event.values[1]);
            tuple.add(event.values[2]);
            try {
                jsonAccXArray.put(event.values[0]);
                jsonAccYArray.put(event.values[1]);
                jsonAccZArray.put(event.values[2]);
                jsonTimestampArray.put(System.currentTimeMillis());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private SensorEventCallback gyroscopeEventCallback = new SensorEventCallback() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            super.onSensorChanged(event);
            if(jsonGyroXArray.length() == ACCELEROMETER_SAMPLING_RATE * SECONDS_TO_SEND_DATA) {
                return;
            }
            ArrayList<Float> tuple = new ArrayList();
            tuple.add(event.values[0]);
            tuple.add(event.values[1]);
            tuple.add(event.values[2]);
            try {
                jsonGyroXArray.put(event.values[0]);
                jsonGyroYArray.put(event.values[1]);
                jsonGyroZArray.put(event.values[2]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private void createJSONObject() {
        try {
            jsonObject.put("timestamp", jsonTimestampArray);
            jsonObject.put("acc_x", jsonAccXArray);
            jsonObject.put("acc_y", jsonAccYArray);
            jsonObject.put("acc_z", jsonAccZArray);
            jsonObject.put("gyro_x", jsonGyroXArray);
            jsonObject.put("gyro_y", jsonGyroYArray);
            jsonObject.put("gyro_z", jsonGyroZArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void clearJSONArrays() {
        jsonAccXArray = new JSONArray();
        jsonAccYArray = new JSONArray();
        jsonAccZArray = new JSONArray();
        jsonGyroXArray = new JSONArray();
        jsonGyroYArray = new JSONArray();
        jsonGyroZArray = new JSONArray();
        jsonTimestampArray = new JSONArray();
        jsonObject = new JSONObject();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("Alex", "Service created");

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
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Alex", "Service onStartCommand");

        String ipAddress = intent.getExtras().getString("ipAddress");

        // starts foreground service with notification
        Intent intentNotification = new Intent(PhoneSensorService.this, MainActivity.class);

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
        sensorManager.registerListener(accelerometerEventCallback, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(gyroscopeEventCallback, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void stopDataReceivingService() {
        stopForeground(true);
        Log.d("Alex", "stopFallDetectionService called");
        stopReceiving();
        stopSelf();
    }

    private void stopReceiving() {
        disconnectFromServer();
        sensorManager.unregisterListener(accelerometerEventCallback);
        sensorManager.unregisterListener(gyroscopeEventCallback);
        isConnected.postValue(false);
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
                        connectedToServer = true;
                        isConnected.postValue(true);
                    });
                } catch (IOException e) {
                    Log.d("Alex", "Failed to connect to server", e);
                    stopDataReceivingService();
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
                    m_Socket.close();
                    handler.post(() -> {
                        connectedToServer = false;
//                        textView.setText("Disconnected from server");
                    });
                } catch (IOException e) {
                    Log.d("Alex", "Failed to disconnect from server", e);
                    stopDataReceivingService();
                }
            }
        }).start();
    }

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
//                            sendFallWarningToWatch();
                            Log.d("Alex", "Fall detected!");
                    } catch (SocketTimeoutException e) {
                        Log.d("Alex", "No acknowledgement received.");
                    }
                } catch (IOException e) {
                    Log.d("Alex", "Failed to send JSON", e);
                }
            }
        }).start();
    }

    class PhoneSensorBinder extends Binder {
        public PhoneSensorService getInstance() {
            return PhoneSensorService.this;
        }
    }
}
