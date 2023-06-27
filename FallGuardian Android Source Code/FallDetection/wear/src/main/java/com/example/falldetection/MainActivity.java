package com.example.falldetection;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.Observer;

import com.example.falldetection.databinding.ActivityMainBinding;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private TextView mTextView;
    private ActivityMainBinding binding;

    private FallDetectionService.FallBinder serviceBinder = null;
    private SensorManager sensorManager;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d("Alex", "MainActivity onServiceConnected");
            serviceBinder = (FallDetectionService.FallBinder) iBinder;
//            serviceBinder.getInstance().currentXYZList.observeForever(accelerometerValueObserver);
            serviceBinder.getInstance().runningLiveData.observeForever(serviceRunningObserver);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("Alex", "MainActivity onServiceDisconnected");
//            serviceBinder.getInstance().currentXYZList.removeObserver(accelerometerValueObserver);
            serviceBinder.getInstance().runningLiveData.observeForever(serviceRunningObserver);
            serviceBinder = null;
        }
    };

    private Observer<Boolean> serviceRunningObserver = new Observer<Boolean>() {
        @Override
        public void onChanged(Boolean aBoolean) {
            String text = aBoolean ? "Service is active" : "Service is not active";
            mTextView.setText(text);
        }
    };

    private Observer<ArrayList<Float>> accelerometerValueObserver = new Observer<ArrayList<Float>>() {
        @Override
        public void onChanged(ArrayList<Float> floatArrayList) {
            String text = "Accelerometer X: " + floatArrayList.get(0) + " Y: "
                    + floatArrayList.get(1) + " Z: " + floatArrayList.get(2);
            mTextView.setText(text);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mTextView = binding.text;

        Intent intent = new Intent(MainActivity.this, FallDetectionService.class);
        this.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

//        sensorManager.registerListener(new SensorEventCallback() {
//            @Override
//            public void onSensorChanged(SensorEvent event) {
//                super.onSensorChanged(event);
//                onSensorChangedCallback(event);
//            }
//        }, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onDestroy() {
        Log.d("Alex", "MainActivity onDestroy");
        super.onDestroy();
        if(serviceConnection != null) {
            unbindService(serviceConnection);
            serviceConnection = null;
        }
    }

    public void onClickStart(View view) {
        Intent intent = new Intent(MainActivity.this, FallDetectionService.class);
        startForegroundService(intent);
        Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show();
    }

    public void onClickStop(View view) {
        if(serviceBinder == null || !serviceBinder.getInstance().isServiceRunning)
            return;
        serviceBinder.getInstance().stopFallDetectionService();
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show();
    }

//    void onSensorChangedCallback(SensorEvent event) {
//        Log.d("Alex", "onSensorChanged X: " + event.values[0] + " Y: "
//                + event.values[1] + " Z: " + event.values[2]);
//
//        String text = "Accelerometer X: " + event.values[0] + " Y: "
//                + event.values[1] + " Z: " + event.values[2];
//        mTextView.setText(text);
//    }
}