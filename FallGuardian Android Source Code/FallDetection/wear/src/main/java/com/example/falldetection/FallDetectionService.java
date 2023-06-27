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
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.JsonWriter;
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
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;

public class FallDetectionService extends Service {

//    public static final float ACCELEROMETER_SAMPLING_RATE = 18.4f;
    public static final float ACCELEROMETER_SAMPLING_RATE = 100f;
//    public static final float ACCELEROMETER_SAMPLING_RATE = 31.0f;
    public static final int SECONDS_TO_SEND_DATA = 5;

    private static final String CHANNEL_ID = "100";
    private static final int NOTIFICATION_ID = 001;
    FallBinder binder = new FallBinder();
    NotificationManager notificationManager;
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private Sensor gyroscopeSensor;
    private DataClient dataClient;
    private MessageClient messageClient;
    boolean isServiceRunning = false;
    public MutableLiveData<Boolean> runningLiveData = new MutableLiveData<>(false);
    private JSONObject jsonObject = new JSONObject();
    private JSONArray jsonAccXArray = new JSONArray();
    private JSONArray jsonAccYArray = new JSONArray();
    private JSONArray jsonAccZArray = new JSONArray();
    private JSONArray jsonGyroXArray = new JSONArray();
    private JSONArray jsonGyroYArray = new JSONArray();
    private JSONArray jsonGyroZArray = new JSONArray();
    private JSONArray jsonTimestampArray = new JSONArray();

//    public MutableLiveData<ArrayList<Float>> currentXYZList = new MutableLiveData<>();

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    DataClient.OnDataChangedListener fallDetectedChangedListener = new DataClient.OnDataChangedListener() {
        @Override
        public void onDataChanged(@NonNull DataEventBuffer dataEventBuffer) {
            for(DataEvent event : dataEventBuffer) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = event.getDataItem();
                    Log.d("Alex", dataItem.getUri().getPath());
                    if (dataItem.getUri().getPath().equals("/fall_detected")) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        boolean hasFallen = dataMap.getBoolean("fall");
                        if(hasFallen) {
                            Intent intent = new Intent(FallDetectionService.this, FallDetectedActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    }
                }
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("Alex", "Service created");

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
//        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        dataClient = Wearable.getDataClient(this);
        messageClient = Wearable.getMessageClient(this);

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
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "FallDetection:FallDetectionWakeLock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Alex", "Service onStartCommand");
        // starts foreground service with notification
        Intent intentNotification = new Intent(FallDetectionService.this, MainActivity.class);

        intentNotification.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Fall Detection Service Running")
                .setContentText("Detecting falls")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        startForeground(NOTIFICATION_ID, mBuilder.build());
        startTracking();

        return super.onStartCommand(intent, flags, startId);
    }

    public void stopFallDetectionService() {
        stopForeground(true);
        Log.d("Alex", "stopFallDetectionService called");
        stopTracking();
        stopSelf();
    }

    SensorEventCallback accelerometerCallback = new SensorEventCallback() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            super.onSensorChanged(event);
            if (jsonAccXArray.length() == ACCELEROMETER_SAMPLING_RATE * SECONDS_TO_SEND_DATA) {
                Log.d("Alex","Acc array full");
                Log.d("Alex", "gyro array length " + jsonGyroXArray.length());
                if (jsonGyroXArray.length() == ACCELEROMETER_SAMPLING_RATE * SECONDS_TO_SEND_DATA) {
                    Log.d("Alex","Gyro array also full");
                    sendJSONObject();
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

    private void sendAccelerometerData(float x, float y, float z) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/accelerometer_data");
        putDataMapRequest.getDataMap().putFloat("x", x);
        putDataMapRequest.getDataMap().putFloat("y", y);
        putDataMapRequest.getDataMap().putFloat("z", z);

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        dataClient.putDataItem(request)
                .addOnSuccessListener(new OnSuccessListener<DataItem>() {
                    @Override
                    public void onSuccess(DataItem dataItem) {
                        Log.d("Alex", "Data sent successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.d("Alex", "Failed to send data", e);
                    }
                });
    }

    private void startTracking() {
        wakeLock.acquire();
//        int sensorDelay = (int) (1000000 / ACCELEROMETER_SAMPLING_RATE); // 1 microsecond divided by sampling rate
        sensorManager.registerListener(accelerometerCallback, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(gyroscopeEventCallback, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST);
//        sensorManager.registerListener(accelerometerCallback, accelerometerSensor, sensorDelay);
//        sensorManager.registerListener(gyroscopeEventCallback, gyroscopeSensor, sensorDelay);
        isServiceRunning = true;
        runningLiveData.postValue(true);
        dataClient.addListener(fallDetectedChangedListener);
//        messageClient.addListener(fallDetectedMessageListener);
//        startSendingData();
    }

    private void stopTracking() {
        clearJSONArrays();
        sensorManager.unregisterListener(accelerometerCallback);
        sensorManager.unregisterListener(gyroscopeEventCallback);
        isServiceRunning = false;
        runningLiveData.postValue(false);
        dataClient.removeListener(fallDetectedChangedListener);
        wakeLock.release();
//        messageClient.removeListener(fallDetectedMessageListener);
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

//    int i = 0;

    private void sendJSONObject() {

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

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/accelerometer_json");
        putDataMapRequest.getDataMap().putString("json_object", jsonObject.toString());

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        dataClient.putDataItem(request)
                .addOnSuccessListener(new OnSuccessListener<DataItem>() {
                    @Override
                    public void onSuccess(DataItem dataItem) {
                        Log.d("Alex", "JSON object sent successfully");
                        clearJSONArrays();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.d("Alex", "Failed to send JSON object", e);
                    }
                });
    }

    public void sendFallConfirmation() {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/fall_confirmation");
        putDataMapRequest.getDataMap().putBoolean("fall", true);
        putDataMapRequest.getDataMap().putLong("timestamp", System.currentTimeMillis());

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        dataClient.putDataItem(request)
                .addOnSuccessListener(new OnSuccessListener<DataItem>() {
                    @Override
                    public void onSuccess(DataItem dataItem) {
                        Log.d("Alex", "Fall confirmation sent successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.d("Alex", "Failed to send fall confirmation", e);
                    }
                });
    }

    public class FallBinder extends Binder {

        public FallDetectionService getInstance() {
            return FallDetectionService.this;
        }
    }
}
