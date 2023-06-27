package com.example.falldetection;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends Fragment {

    MainViewModel mainViewModel;
    TextView serverStatusTextView;
    TextView smartwatchStatusTextView;
    TextView fallDetectionStatusTextView;
    EditText editTextIPAddress;
    Button buttonStart;
    ImageView iconWatch;
    ImageView iconServer;

    TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            mainViewModel.setIPAddress(charSequence.toString());
        }

        @Override
        public void afterTextChanged(Editable editable) {

        }
    };

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment HomeFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
//        textView = getActivity().findViewById(R.id.textView);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
//        return inflater.inflate(R.layout.fragment_home, container, false);
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        serverStatusTextView = view.findViewById(R.id.serverStatusTextView);
        smartwatchStatusTextView = view.findViewById(R.id.smartwatchStatusTextView);
        fallDetectionStatusTextView = view.findViewById(R.id.statusTextView);
        editTextIPAddress = view.findViewById(R.id.editTextIPAddress);
        buttonStart = view.findViewById(R.id.buttonStart);
        iconWatch = view.findViewById(R.id.iconWatch);
        iconServer = view.findViewById(R.id.iconServer);

        editTextIPAddress.setText(mainViewModel.getIPAddress());
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editTextIPAddress.addTextChangedListener(textWatcher);
//        mainViewModel.isConnectedToService.observe();
//        mainViewModel.connectedString.observe(getViewLifecycleOwner(), s -> serverStatusTextView.setText(s));
        mainViewModel.isConnectedToService.observe(getViewLifecycleOwner(), bool -> {
            if(bool) {
//                buttonStart.setBackgroundColor(Color.RED);
//                buttonStart.setText("Stop Fall Detection");
//                fallDetectionStatusTextView.setText("Fall Detection Enabled");
            }
            else {
//                buttonStart.setBackgroundColor(Color.GREEN);
//                buttonStart.setText("Start Fall Detection");
//                fallDetectionStatusTextView.setText("Fall Detection Disabled");
            }
        });

        mainViewModel.isConnectedToWatch.observe(getViewLifecycleOwner(), bool -> {
            if(bool) {
                iconWatch.setImageResource(R.drawable.ic_bluetooth_green);
//                smartwatchStatusTextView.setText("Connected to smartwatch");
            }
            else {
                iconWatch.setImageResource(R.drawable.ic_bluetooth_red);
//                smartwatchStatusTextView.setText("Not connected to smartwatch");
            }

            mainViewModel.serverStatus.observe(getViewLifecycleOwner(), serverStatus -> {
                if(serverStatus == DataReceivingService.ServerStatus.CONNECTING) {
                    fallDetectionStatusTextView.setText("Connecting...");
                    buttonStart.setAlpha(0.3f);
                    buttonStart.setClickable(false);
                }
                else if(serverStatus == DataReceivingService.ServerStatus.CONNECTED) {
                    buttonStart.setBackgroundColor(Color.RED);
                    buttonStart.setText("Stop Fall Detection");
                    fallDetectionStatusTextView.setText("Fall Detection Enabled");
                    buttonStart.setAlpha(1.0f);
                    buttonStart.setClickable(true);
                    iconServer.setImageResource(R.drawable.ic_wifi_green);
                }
                else {
//                    buttonStart.setBackgroundColor(Color.GREEN);
                    buttonStart.setBackgroundColor(0xFF008000);
                    buttonStart.setText("Start Fall Detection");
                    fallDetectionStatusTextView.setText("Fall Detection Disabled");
                    buttonStart.setAlpha(1.0f);
                    buttonStart.setClickable(true);
                    iconServer.setImageResource(R.drawable.ic_wifi_red);
                }
            });
        });
    }

//    public void changeTextView(String text) {
//        textView.setText(text);
//    }

//    public String getEditTextString() {
//        return editTextIPAddress.getText().toString();
//    }
}