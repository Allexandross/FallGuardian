package com.example.falldetection;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

public class MainViewModel extends AndroidViewModel {

    MutableLiveData<Boolean> isConnectedToService = new MutableLiveData<>(false);
    MutableLiveData<Boolean> isConnectedToWatch = new MutableLiveData<>(false);
//    MutableLiveData<String> ipAddressString = new MutableLiveData<>("192.168.1.108");
    MutableLiveData<String> ipAddressString = new MutableLiveData<>("34.105.148.87");
    MutableLiveData<String> connectedString = new MutableLiveData<>("");
    MutableLiveData<String> contactName = new MutableLiveData<>("");
    MutableLiveData<String> contactNumber = new MutableLiveData<>("");
    SharedPreferences sharedPreferences;
    MutableLiveData<DataReceivingService.ServerStatus> serverStatus = new MutableLiveData<>(DataReceivingService.ServerStatus.DISCONNECTED);

    SharedPreferences.OnSharedPreferenceChangeListener sharedPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.d("Alex", "SharedPref onChangedListener called");
            if(key.equals("contact_name")) {
                contactName.postValue(sharedPreferences.getString("contact_name", "No contact set"));
            }
            else if(key.equals("contact_number")) {
                contactNumber.postValue(sharedPreferences.getString("contact_number", ""));
            }
        }
    };


    public MainViewModel(@NonNull Application application) {
        super(application);
        sharedPreferences = application.getSharedPreferences("ContactPrefs", Context.MODE_PRIVATE);
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPrefListener);

        contactName.postValue(sharedPreferences.getString("contact_name", "No contact set"));
        contactNumber.postValue(sharedPreferences.getString("contact_number", ""));
    }

    public boolean getIsConnectedToService() {
        return isConnectedToService.getValue();
    }

    public void setIsConnectedToService(boolean value) {
        isConnectedToService.postValue(value);
    }

    public String getIPAddress() {
        return ipAddressString.getValue();
    }

    public void setIPAddress(String ipAddress) {
        ipAddressString.postValue(ipAddress);
    }

    public String getConnectedString() {
        return connectedString.getValue();
    }

    public void setConnectedString(String connected) {
        connectedString.postValue(connected);
    }

    public String getContactName() {
        return contactName.getValue();
    }

    public void setContactName(String name) {
        contactName.postValue(name);
    }

    public String getContactNumber() {
        return contactNumber.getValue();
    }

    public void setContactNumber(String number) {
        contactNumber.postValue(number);
    }

    public boolean getConnectedToWatch() {
        return isConnectedToWatch.getValue();
    }

    public void setConnectedToWatch(boolean value) {
        isConnectedToWatch.postValue(value);
    }

    public void setServerStatus(DataReceivingService.ServerStatus status) {
        serverStatus.postValue(status);
    }

    public void updateSharedPreferencesContact(String contactName, String contactNumber) {
        Log.d("Alex", "updateSharedPrefContact called");
        SharedPreferences sharedPreferences = getApplication().getSharedPreferences("ContactPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("contact_name", contactName);
        editor.putString("contact_number", contactNumber);
        editor.apply();
    }
}
