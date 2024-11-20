package com.example.rgbmems_smartphoneapp;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.os.Handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONException;
import org.json.JSONObject;

public class ConnectToServer {
    public static Socket client;
    public String serverIp = "10.0.2.2"; // Change to your server's IP address
    public int serverPort = 8000; // Port on which the server is listening
    private Handler handler = new Handler(); // Initialize the Handler
    private TextView responseTextView; // Declare TextView
    private static final String TAG = "ConnectServer";
    private OutputStream outputStream; // Output stream for sending data
    private InputStream inputStream; // Input stream for receiving data
    private boolean isReconnecting = false; // Flag to check if a reconnection attempt is in progress
    private ConnectionViewModel connectionViewModel;
    private ConcurrentLinkedQueue<PendingMessage> pendingQueue = new ConcurrentLinkedQueue<>(); // Use a queue to manage pending messages
    private boolean isWaitingForResponse = false; // Flag to check if waiting for a response from the server

    public void setResponseTextView(TextView responseTextView) {
        this.responseTextView = responseTextView; // Assign TextView from MainActivity
    }

    public void setConnectionViewModel(ConnectionViewModel viewModel) {
        this.connectionViewModel = viewModel;
        Log.d("ConnectToServer", "ConnectionViewModel set: " + viewModel);
    }
    public void connectToServer(Context context, Switch toggle_connect) {
        new Thread(() -> {
            try {
                if (client == null || client.isClosed()) {
                    client = new Socket(serverIp, serverPort);
                    // Set timeout for the socket
                    //client.setSoTimeout(30000); // 30 seconds timeout for receiving data
                    Log.d(TAG, "Connected to server");
                }

                if (client != null && client.isConnected()) {
                    // Connection successful
                    Log.d("ConnectionStatus", "Connection successful");
                    // Check connectionViewModel
                    if (connectionViewModel != null) {
                        connectionViewModel.setConnectionStatus(true); // Update connection status
                    } else {
                        Log.d(TAG, "ConnectionViewModel is null, unable to update connection status.");
                    }
                    // Read the response from the server
                    InputStreamReader InStr = new InputStreamReader(client.getInputStream(),"UTF-8");

                    try {
                        // Set the timeout (2 seconds)
                        int retryDelay = 2000; // 2000ms = 2 seconds
                        int retries = 3; // Retry count

                        boolean responseReceived = false;

                        for (int i = 0; i < retries; i++) {
                            // Check if there is a response from the server
                            if (InStr.ready()) {
                                Log.d(TAG, "Response from server: OK");
                                responseReceived = true;
                                break; // Exit the loop if a response is received
                            } else {
                                Log.d(TAG, "Response from server: NULL, retrying...");
                                Thread.sleep(retryDelay); // Wait for the delay period before retrying
                            }
                        }

                        if (!responseReceived) {
                            Log.d(TAG, "No response from server after retries.");
                            ((MainActivity) context).runOnUiThread(() -> {
                                // Display the response from the server on the screen using TextView
                                toggle_connect.setChecked(false);
                            });
                        }
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Error while waiting for server response: " + e.getMessage());
                    }

                    // Check and send the pending messages
                    if (pendingQueue != null && !pendingQueue.isEmpty()) {
                        Log.d(TAG, "Pending message found. Sending message...");
                        Log.d(TAG, "Size of pendingMessage: " + pendingQueue.size()); // Check the pendingMessage list

                        sendAllPendingMessages();
                    }

                    BufferedReader in = new BufferedReader(InStr);

                    // Thread for handling the server's response
                    new Thread(() -> {
                        try {
                            String serverResponse;
                            while ((serverResponse = in.readLine()) != null) {
                                Log.d(TAG, "Server response: " + serverResponse);

                                // Update the user interface with the response from the server
                                String finalResponse = serverResponse;
                                ((MainActivity) context).runOnUiThread(() -> {
                                    // Display the response from the server on the screen using TextView
                                    if (responseTextView != null) {
                                        updateResponseText(finalResponse); // Call the method to update and hide the TextView
                                    }
                                });

                                // After receiving the response, send the next message
                                processNextPendingMessage();
                            }
                        } catch (IOException e) {
                            Log.d(TAG, "Error reading server response: " + e.getMessage(), e);
                        }
                    }).start();

                } else {
                    // Connection failed
                    Log.d(TAG, "Not connected to server!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Error connecting to server", e);
            }
        }).start();
    }

    //Method to create a new PendingMessage object with the specified messageType and messageValue
    public void setPendingMessage(String messageType, int messageValue) {
        PendingMessage newMessage = new PendingMessage(messageType, messageValue);
        pendingQueue.add(newMessage); // Add to the queue
        Log.d(TAG, "Pending message stored: " + messageType + " - " + messageValue);

        // If no response is awaited, process immediately
        if (!isWaitingForResponse) {
            processNextPendingMessage();
        }
    }
    // Process the next message in the queue
    private void processNextPendingMessage() {
        if (!pendingQueue.isEmpty() && isConnected()) {
            PendingMessage nextMessage = pendingQueue.poll(); // Get the next message
            if (nextMessage != null) {
                isWaitingForResponse = true; // Set the flag to indicate waiting for a response
                sendMessageToServer(nextMessage.getName(), nextMessage.getCheckNumber());
            }
        } else {
            isWaitingForResponse = false; // The queue is empty or not yet connected
        }
    }

    // Method to send all PendingMessages after reconnecting
    public void sendAllPendingMessages() {
        if (isConnected()) {  // Check if the connection has been established
            processNextPendingMessage();
        } else {
            Log.e(TAG, "Connection not established. Cannot send pending messages.");
        }
    }

    // Send message to the server
    public void sendMessageToServer(String type, int value) {
        new Thread(() -> {
            try {
                //if (client != null && !client.isClosed()) {
                if (isConnected()) {
                    // Create a JSON object from type and value
                    JSONObject jsonMessage = new JSONObject();
                    jsonMessage.put("type", type);
                    jsonMessage.put("value", value);

                    // Call getOutputStream and check
                    if (client != null && !client.isClosed()) {
                        // Initialize outputStream from the client's output stream
                        outputStream = client.getOutputStream();
                    } else {
                        Log.e(TAG, "Connection lost before sending message.");
                        return;
                    }
                    // Send message to the server
                    // メッセージを送信する前にoutputStreamがnullでないことを確認する
                    if (outputStream != null) {
                        synchronized (outputStream) {
                            outputStream.write((jsonMessage.toString() + "\n").getBytes());
                            outputStream.flush();
                        }
                        Log.d("ClientThread", "Message sent to server: " + jsonMessage.toString());
                    } else {
                        Log.e("ClientThread", "OutputStream is null. Cannot send message.");
                    }
                }
                else {
                    Log.e("ClientThread", "Connection not established. Message not sent.");
                }
            } catch (IOException | JSONException e) {
                Log.d(TAG, "Error sending message to server", e);
            }
        }).start();
    }

    // Check if the client is connected to the server
    public boolean isConnected() {
        return client != null && client.isConnected() && !client.isClosed();
    }

    // Update the content of the TextView and hide it after a certain period of time
    public void updateResponseText(String response) {
        if (responseTextView != null) {
            responseTextView.setText(response);
            responseTextView.setVisibility(View.VISIBLE); // Show the TextView

            // Set a timer to hide the TextView
            handler.postDelayed(() -> responseTextView.setVisibility(View.GONE), 3000); // Hide after 3 seconds
        }
    }

    public void connect() {
        new Thread(() -> {
            try {
                // Connect to socket
                if (client == null || client.isClosed()) {
                    client = new Socket(serverIp, serverPort);
                }
                // Set timeout for the socket
                client.setSoTimeout(30000); // 30 seconds timeout for receiving data
                outputStream = client.getOutputStream();
                inputStream = client.getInputStream();
                Log.d(TAG, "Connected to server");
            } catch (IOException e) {
                Log.d(TAG, "Error connecting to server: " + e.getMessage(), e);
            }
        }).start();
    }

    // Continuously check connection; if not connected, attempt to reconnect
    private void ensureConnected() {
        if (!isConnected() && !isReconnecting) {
            isReconnecting = true; // Set flag to indicate reconnection attempt
            connect(); // Attempt to connect
            isReconnecting = false; // Reset flag after connection attempt
        }
    }

    // Update sendImage method to send only the image without the image sequence number
    public void sendImage(byte[] imageData) {
        new Thread(() -> {
            ensureConnected(); // Ensure connection before sending image
            try {
                outputStream = client.getOutputStream();
                inputStream = client.getInputStream();
                if (isConnected()) {
                    // Check image data
                    if (imageData == null || imageData.length == 0) {
                        Log.d(TAG, "Image data is null or empty");
                        return; // Return early if there is no image data
                    }

                    JSONObject jsonMessage_img = new JSONObject();
                    jsonMessage_img.put("type", "sendimage");
                    jsonMessage_img.put("value", SecondFragment.currentNumber);   //画像番号
                    //jsonMessage_img.put("ImageDataByteArray", imageData);         //画像のbyte配列

                    //Send data through the socket
                    synchronized (outputStream) {
                        outputStream.write((jsonMessage_img.toString() + "\n").getBytes());
                        outputStream.flush(); // Ensure all data is sent
                    }

                    //Introduce a small delay to allow server to process JSON message
                    Thread.sleep(100);  // Delay of 100ms (can be adjusted)

                    //Send the length of image data
                    int imageLength = imageData.length;
                    synchronized (outputStream) {
                        outputStream.write((imageLength + "\n").getBytes()); // Send the length as a new line
                        outputStream.flush(); // Ensure length is sent
                    }

                    //Introduce another small delay before sending the image data
                    Thread.sleep(100);  // Delay of 100ms (can be adjusted)

                    //Send the actual image data
                    synchronized (outputStream) {
                        outputStream.write(imageData); // Write the image data to output stream
                        outputStream.flush(); // Ensure all data is sent
                    }

                    Log.d(TAG, "Image sent to server");

                    // Read response from server
                    byte[] responseBuffer = new byte[4096];
                    int bytesRead = inputStream.read(responseBuffer); // Read response
                    if (bytesRead > 0) {
                        String response = new String(responseBuffer, 0, bytesRead);
                        Log.d(TAG, "Server response: " + response);
                    } else {
                        Log.d(TAG, "No response from server");
                    }
                } else {
                    Log.d(TAG, "Socket is not connected");
                }
            } catch (SocketException e) {
                Log.d(TAG, "Socket error: " + e.getMessage(), e);
                disconnect();
            } catch (SocketTimeoutException e) {
                Log.d(TAG, "Socket timeout: " + e.getMessage(), e);
                disconnect();
            } catch (IOException | JSONException e) {
                Log.d(TAG, "Error sending image: " + e.getMessage(), e);
                disconnect();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    //Disconnects the client by closing input and output streams
    public void disconnect() {
        try {
            if (inputStream != null) {
                inputStream.close(); // Close input stream
            }
            if (outputStream != null) {
                outputStream.close(); // Close output stream
            }
            if (client != null && !client.isClosed()) {
                client.close(); // Close socket if it's not already closed
                Log.d(TAG, "Socket closed");
            }
        } catch (IOException e) {
            Log.d(TAG, "Error closing socket", e);
        }
    }
}

