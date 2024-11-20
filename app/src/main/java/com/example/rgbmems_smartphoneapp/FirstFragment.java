package com.example.rgbmems_smartphoneapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.widget.ListPopupWindow;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

public class FirstFragment extends Fragment {
    private Switch toggle_connect;
    private Switch toggle_onoff;
    private PendingMessage pendingMessage = null; // Store the pending message to be sent
    private ConnectToServer connectToServer;
    private Button sendButton;
    private String type = "";
    private int value;
    private TextView responseTextView;
    private ConnectionViewModel connectionViewModel;
    // Declare a handler for retrying connections
    private Handler retryHandler = new Handler();
    private Runnable retryRunnable;

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_first, container, false);
        // Initialize ViewModel
        connectionViewModel = new ViewModelProvider(requireActivity()).get(ConnectionViewModel.class);

        // Initialize the views here
        responseTextView = view.findViewById(R.id.responseTextView);
        toggle_connect = view.findViewById(R.id.switch1);
        toggle_onoff = view.findViewById(R.id.switch2);
        sendButton = view.findViewById(R.id.button3);

        // Initialize ConnectToServer
        connectToServer = new ConnectToServer();
        connectToServer.setResponseTextView(responseTextView); // Assign the TextView to the ConnectToServer class

        // Set up the spinner
        setupSpinner(view);

        connectToServer.setConnectionViewModel(connectionViewModel); // Call method to set up ViewModel

        // Handle the event when the state of toggle_connect changes
        toggle_connect.setOnCheckedChangeListener((buttonView, isChecked) -> handleToggleConnect(isChecked));

        // Set initial state and connect to server
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            toggle_connect.setChecked(true); // Set switch1 to ON
        }, 50); // 300 milliseconds = 0.3 seconds

        // Handle the event when the state of toggle_onoff changes
        toggle_onoff.setOnCheckedChangeListener((buttonView, isChecked) -> handleToggleOnOff(isChecked));

        // Handle the event when the sendButton is clicked
        sendButton.setOnClickListener(v -> handleSendButtonClick());

        return view; // Return the inflated view
    }



    private int currentIndex = 0;

    @SuppressLint("ClickableViewAccessibility")
    private void setupSpinner(View view) {
        Spinner dropdown = view.findViewById(R.id.spinner);
        Button btBack = view.findViewById(R.id.btBack);
        Button btNext = view.findViewById(R.id.btNext);

        // Set up list of numbers from 0 to 99
        List<String> numbersList = new ArrayList<>();
        for (int i = 0; i <= 99; i++) {
            numbersList.add(String.valueOf(i));
        }

        // Set up ArrayAdapter and ListPopupWindow for Spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, numbersList);
        dropdown.setAdapter(adapter);

        ListPopupWindow listPopupWindow = new ListPopupWindow(requireContext());
        listPopupWindow.setAnchorView(dropdown);
        listPopupWindow.setAdapter(adapter);
        listPopupWindow.setHeight(450);
        listPopupWindow.setModal(true);

        // Show ListPopupWindow when Spinner is touched
        dropdown.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                listPopupWindow.show();
            }
            return true;
        });

        // Update selection when item in ListPopupWindow is clicked
        listPopupWindow.setOnItemClickListener((parent, view1, position, id) -> {
            currentIndex = position;
            dropdown.setSelection(currentIndex);
            listPopupWindow.dismiss();
        });

        // Set up Back Button to decrement the number
        btBack.setOnClickListener(v -> {
            if (currentIndex > 0) {  // Only decrement if currentIndex is greater than 0
                currentIndex--;
                dropdown.setSelection(currentIndex);

            }
        });

        // Set up Next Button to increment the number
        btNext.setOnClickListener(v -> {
            if (currentIndex < 99) {  // Only increment if currentIndex is less than 99
                currentIndex++;
                dropdown.setSelection(currentIndex);

            }
        });
    }


    private void handleToggleConnect(boolean isChecked) {
        if (isChecked) {
            // When Switch 1 is turned on, establish a connection to the server
            connectToServer.connectToServer(getActivity(),toggle_connect);
            // Set up retry mechanism if connection fails
            retryRunnable = new Runnable() {
                @Override
                public void run() {
                    Boolean isConnected = connectionViewModel.getConnectionStatus().getValue();

                    // Check for null and ensure the connection status is false
                    if (isConnected == null || !isConnected) {
                        Log.d("ConnectServer", "Retrying server connection...");
                        connectToServer.connectToServer(getActivity(),toggle_connect);
                        retryHandler.postDelayed(this, 5000); // Retry every 5 seconds
                    } else {
                        // Stop retrying once connected
                        retryHandler.removeCallbacks(retryRunnable);
                        Log.d("ConnectServer", "Connection established, stopping retries.");
                    }
                }
            };
            retryHandler.postDelayed(retryRunnable, 5000); // Start retrying in 5 seconds
        } else {
            // When Switch 1 is turned off, disconnect from the server
            connectToServer.disconnect(); // Call the disconnect method
            connectionViewModel.setConnectionStatus(false); // Update connection status
            connectToServer.updateResponseText("切断");
            retryHandler.removeCallbacks(retryRunnable); // Stop retries if toggle is off
        }
    }

    private void handleToggleOnOff(boolean isChecked) {
        type = "turnonoff";
        value = isChecked ? 1 : 0;

        if (toggle_connect.isChecked()) {
            if (connectToServer.isConnected()){
                // If Switch 1 is on, send the message immediately
                connectToServer.sendMessageToServer(type, value);
            } else {
                connectToServer.setPendingMessage(type, value);
            }
        } else {
            // If Switch 1 is off, store the pending message to be sent later
            connectToServer.setPendingMessage(type, value);
        }
    }

    private void handleSendButtonClick() {
        type = "sendnumber";
        Spinner dropdown = getView().findViewById(R.id.spinner); // Use getView() to access the Spinner
        int selectedNumber = Integer.parseInt(dropdown.getSelectedItem().toString());
        if (connectToServer.isConnected()) {
            connectToServer.sendMessageToServer(type, selectedNumber);
        } else {
            connectToServer.setPendingMessage(type, selectedNumber); // Store the pending message to be sent later
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (toggle_connect.isChecked()) {
            Log.d("ConnectionStatus", "toggle_connect is ON. Attempting to connect to server.");
            if (!connectToServer.isConnected()) {
                // Ensure the server connection is established when the fragment becomes visible
                connectToServer.connectToServer(requireActivity(),toggle_connect);
                Log.d("ConnectionStatus", "Attempting to connect to server in onResume.");
                if (!connectToServer.isConnected()){
                    Log.d("ConnectServer", "Failed to connect to server");
                }
                if (pendingMessage != null) {
                    connectToServer.sendMessageToServer(pendingMessage.getName(), pendingMessage.getCheckNumber());
                    pendingMessage = null; // Clear the pending message after sending
                }
            } else {
                Log.d("ConnectionStatus", "Already connected to server.");
            }
        } else {
            Log.d("ConnectionStatus", "toggle_connect is off. Not connected to server.");
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        // When disconnected
        connectionViewModel.setConnectionStatus(false); // Update connection status
    }
}
