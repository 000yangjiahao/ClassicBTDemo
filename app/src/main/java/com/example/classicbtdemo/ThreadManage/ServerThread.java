package com.example.classicbtdemo.ThreadManage;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

public class ServerThread extends Thread {
    private final BluetoothServerSocket serverSocket;
    private final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private Handler handler;
    private OnServerConnectListener connectListener;

    public interface OnServerConnectListener {
        void onServerConnect(BluetoothSocket socket);
        void onServerError(String errorMsg);
    }

    public void setOnServerConnectListener(OnServerConnectListener listener) {
        this.connectListener = listener;
    }

    @SuppressLint("MissingPermission")
    public ServerThread(BluetoothAdapter adapter, Handler handler) {
        this.handler = handler;
        BluetoothServerSocket tmpSocket = null;
        try {
            tmpSocket = adapter.listenUsingRfcommWithServiceRecord("BluetoothServer", MY_UUID);
        } catch (IOException e) {
            Log.e("ServerThread", "Error creating server socket", e);
        }
        serverSocket = tmpSocket;
    }

    public void cancel() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            Log.e("ServerThread", "Error closing server socket", e);
        }
    }

    @Override
    public void run() {
        BluetoothSocket socket = null;
        while (true) {
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                Log.e("ServerThread", "Error accepting connection", e);
                if (connectListener != null) {
                    connectListener.onServerError("Error accepting connection");
                }
                break;
            }
            if (socket != null) {
                try {
                    serverSocket.close();
                    if (connectListener != null) {
                        connectListener.onServerConnect(socket);
                    }
                    break;
                } catch (IOException e) {
                    Log.e("ServerThread", "Error closing server socket", e);
                }
            }
        }
    }
}
