package com.example.falldetection;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.falldetection.databinding.ActivityFallDetectedBinding;
import com.example.falldetection.databinding.ActivityMainBinding;

public class FallDetectedActivity extends Activity {

    private TextView mTextView;
    private ActivityFallDetectedBinding binding;
    private boolean countdownRunning;


    FallDetectionService.FallBinder serviceBinder;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serviceBinder = (FallDetectionService.FallBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_fall_detected);

        binding = ActivityFallDetectedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mTextView = binding.textFallDetected;


        Intent intent = new Intent(FallDetectedActivity.this, FallDetectionService.class);
        this.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE));
        startCountdown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(serviceConnection != null) {
            unbindService(serviceConnection);
            serviceConnection = null;
        }
    }

    public void fallConfirmed() {
        serviceBinder.getInstance().sendFallConfirmation();
        countdownRunning = false;
        finish();
    }

    public void onClickHelp(View view) {
        Toast.makeText(this, "You fell! Alerting your emergency contact now!", Toast.LENGTH_SHORT).show();
        fallConfirmed();
    }

    public void onClickImOk(View view) {
        Toast.makeText(this, "You did not fall", Toast.LENGTH_SHORT).show();
        countdownRunning = false;
        finish();
    }

    public void startCountdown() {
        Handler handler = new Handler();
        int MILLISECONDS_TO_WAIT = 15000; // equal to 15 seconds
        long timeStarted = System.currentTimeMillis();
        long finalTime = timeStarted + MILLISECONDS_TO_WAIT;
        countdownRunning = true;

        new Thread(() -> {
            Log.d("Alex", "Countdown thread started");
            while(countdownRunning) {
                long currentTime = System.currentTimeMillis();
                if(currentTime >= finalTime) {
                    fallConfirmed();
                }
                handler.post(() -> {
                    int timeLeft = (int) ((finalTime - currentTime) / 1000);
                    mTextView.setText(getResources().getString(R.string.fall_question) + "\nAlerting in " + timeLeft);
                });

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.d("Alex", "Countdown thread ended");
        }).start();
    }
}