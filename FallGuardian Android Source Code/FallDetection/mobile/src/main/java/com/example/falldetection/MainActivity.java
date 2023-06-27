package com.example.falldetection;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity {

    public static final String CAPABILITY_WEAR_APP = "verify_fall_detection_app_wear";

    private MainViewModel mainViewModel;
    private DataReceivingService.DataReceivingBinder serviceBinder = null;
    private boolean isWatchConnected = false;
    private CapabilityClient capabilityClient;

    ActivityResultLauncher<Intent> activityResultLauncher;

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d("Alex", "onServiceConnected");
//            serviceBinder = (DataReceivingService.DataReceivingBinder) iBinder;
            serviceBinder = (DataReceivingService.DataReceivingBinder) iBinder;
            serviceBinder.getInstance().isConnectedToServer.observeForever(isRunningObserver);
            serviceBinder.getInstance().serverStatus.observeForever(serverStatusObserver);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d("Alex", "onServiceDisconnected");
            serviceBinder.getInstance().isConnectedToServer.removeObserver(isRunningObserver);
            serviceBinder.getInstance().serverStatus.removeObserver(serverStatusObserver);

            serviceBinder = null;
        }
    };

    private Observer<Boolean> isRunningObserver = new Observer<Boolean>() {
        @Override
        public void onChanged(Boolean aBoolean) {
            if(aBoolean) {
                mainViewModel.setConnectedString("Connected to server");
            }
            else {
                mainViewModel.setConnectedString("Disconnected from server");
            }
        }
    };

    private Observer<DataReceivingService.ServerStatus> serverStatusObserver = new Observer<DataReceivingService.ServerStatus>() {
        @Override
        public void onChanged(DataReceivingService.ServerStatus serverStatus) {
            mainViewModel.setServerStatus(serverStatus);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_fragment);
//        NavController navController = navHostFragment.getNavController();
        mainViewModel = new ViewModelProvider(this,
                (ViewModelProvider.Factory) ViewModelProvider.AndroidViewModelFactory.getInstance(this.getApplication())).get(MainViewModel.class);

        capabilityClient = Wearable.getCapabilityClient(this);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            switch(item.getItemId()) {
                case R.id.homeFragment:
                    replaceFragment(new HomeFragment());
                    break;
                case R.id.settingsFragment:
                    replaceFragment(new SettingsFragment());
                    break;
                case R.id.aboutFragment:
                    replaceFragment(new AboutFragment());
                    break;
            }
            return true;
        });

        if (findViewById(R.id.main_frame) != null) {
            if (savedInstanceState != null)
                return;
            replaceFragment(new HomeFragment());
        }

        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Uri contactUri = data.getData();

                        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};

                        Cursor cursor = this.getContentResolver().query(contactUri, projection, null, null, null);
                        try {
                            if(cursor.getCount() == 0)
                                return;

                            cursor.moveToFirst();
                            String contactName = cursor.getString(0);
                            String contactNumber = cursor.getString(1);
                            mainViewModel.updateSharedPreferencesContact(contactName, contactNumber);
//                            Log.d("Alex", "Contact name: " + contactName);
//                            Log.d("Alex", "Contact number: " + contactNumber);
                        } finally {
                            cursor.close();
                        }
                    }
                });

        checkWearOsConnectedUsingHandler();

//        capabilityClient.addListener(onCapabilityChangedListener, "wear://");

        Intent intent = new Intent(MainActivity.this, DataReceivingService.class);
//        Intent intent = new Intent(MainActivity.this, PhoneSensorService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.main_frame, fragment);
        fragmentTransaction.commit();
    }

    public void onClickConnect(View view) {
        Log.d("Alex", "onClickConnect pressed");
//        isSmartwatchConnected();
//        boolean isSmartwatchConnected = checkWearOsCapability();
//        boolean isSmartwatchConnected = hasWearOsSmartwatchConnected();
        if(!isWatchConnected) {
            Log.d("Alex", "IS NOT CONNECTED, ASK USER");
            FragmentManager fragmentManager = getSupportFragmentManager();
            checkWearOsConnectedUsingHandler();
            DialogFragment dialog = new WatchConnectionDialogFragment();
            dialog.show(fragmentManager, "watch_connection_dialog");
            return;
        }
        else {
            Log.d("Alex", "IS CONNECTED, CONTINUE");
        }

        if(serviceConnection == null || serviceBinder.getInstance().serverStatus.getValue() == DataReceivingService.ServerStatus.DISCONNECTED) {
//            serviceBinder.getInstance().connectToServer(editTextIPAddress.getText().toString());
            Intent intent = new Intent(MainActivity.this, DataReceivingService.class);
//            Intent intent = new Intent(MainActivity.this, PhoneSensorService.class);
//            intent.putExtra("ipAddress", editTextIPAddress.getText().toString());
            intent.putExtra("ipAddress", mainViewModel.getIPAddress());
            startForegroundService(intent);
            mainViewModel.setIsConnectedToService(true);
        }
        else {
//            serviceBinder.getInstance().disconnectFromServer();
            serviceBinder.getInstance().stopDataReceivingService();
//            Intent intent = new Intent(MainActivity.this, DataReceivingService.class);
//            stopService(intent);
            mainViewModel.setIsConnectedToService(false);
        }
    }

    public void onClickWarning(View view) {
//        serviceBinder.getInstance().sendFallWarningToWatch();
    }

    public void onClickPickContact(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        activityResultLauncher.launch(intent);
    }

    private boolean checkWearOsConnected() {
        try {
            Task<CapabilityInfo> capabilityInfoTask = capabilityClient.getCapability(
                    CAPABILITY_WEAR_APP,
                    CapabilityClient.FILTER_REACHABLE);
            CapabilityInfo capabilityInfo = Tasks.await(capabilityInfoTask, 5000, TimeUnit.MILLISECONDS);
            Set<Node> connectedNodes = capabilityInfo.getNodes();
            if (connectedNodes.size() > 0) {
                for (Node node: connectedNodes) {
                    Log.d("Alex", "Node: " + node.getDisplayName());
                    Log.d("Alex", "Node: " + node.getId());
                    Log.d("Alex", "isNearby: " + node.isNearby());
                    return node.isNearby();
//                    return true;
                }
            } else {
                Log.d("Alex", "hasWearOSSmartwatchConnected size <= 0");
                return false;
            }
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private void checkWearOsConnectedUsingHandler() {
        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.isWatchConnected = checkWearOsConnected();
                mainViewModel.setConnectedToWatch(isWatchConnected);
            }
        });
    }
}