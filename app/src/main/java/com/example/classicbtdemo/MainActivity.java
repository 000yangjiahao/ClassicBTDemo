package com.example.classicbtdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.classicbtdemo.ThreadManage.ConnectThread;
import com.example.classicbtdemo.ThreadManage.ConnectedThread;
import com.example.classicbtdemo.ThreadManage.ServerThread;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final String TAG = "BluetoothApp";
    private static final String APP_NAME = "BluetoothApp";
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> deviceArrayAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;
    private BluetoothSocket bluetoothSocket;

    private EditText etMessage;
    private TextView tvReceived;
    private Button btnSend;
    private Button btnDisconnect;
    private Button btnSearch;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private ServerThread serverThread;
    private Handler handler;

    private static final String DISCONNECT_MESSAGE = "__DISCONNECT__";

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initListener();
        initAdapter();
        checkPermissions();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        handler = new Handler(msg -> {
            if (msg.what == 0) {
                String receivedMessage = (String) msg.obj;
                tvReceived.append("Received: " + receivedMessage + "\n");
            } else if (msg.what == 1) {
                Toast.makeText(MainActivity.this, "Disconnected by remote device", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        startServerThread();
    }

    private void initView() {
        btnSearch = findViewById(R.id.btn_search);
        etMessage = findViewById(R.id.et_message);
        tvReceived = findViewById(R.id.tv_received);
        btnSend = findViewById(R.id.btn_send);
        btnDisconnect = findViewById(R.id.btn_disconnected);
    }

    private void initListener() {
        btnSearch.setOnClickListener(v -> searchBluetoothDevices());
        btnSend.setOnClickListener(v -> sendMessage());
        btnDisconnect.setOnClickListener(v -> disconnect());
    }

    @SuppressLint("MissingPermission")
    private void initAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothDevices = new ArrayList<>();
        deviceArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        } else if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void checkPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private void startServerThread() {
        serverThread = new ServerThread(bluetoothAdapter, handler);
        serverThread.setOnServerConnectListener(new ServerThread.OnServerConnectListener() {
            @Override
            public void onServerConnect(BluetoothSocket socket) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected as server", Toast.LENGTH_SHORT).show());
                manageConnectedSocket(socket);
            }

            @Override
            public void onServerError(String errorMsg) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Server error: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
        serverThread.start();
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        // 在连接之前断开现有连接
        disconnect();

        connectThread = new ConnectThread(bluetoothAdapter, device, MY_UUID.toString());
        connectThread.setOnBluetoothConnectListener(new ConnectThread.OnBluetoothConnectListener() {
            @Override
            public void onStartConn() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connecting to " + device.getName(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onConnSuccess(BluetoothSocket socket) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show());
                manageConnectedSocket(socket);
            }

            @Override
            public void onConnFailure(String errorMsg) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection failed: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
        connectThread.start();
    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        connectedThread = new ConnectedThread(socket);
        connectedThread.setOnSendReceiveDataListener(new ConnectedThread.OnSendReceiveDataListener() {
            @Override
            public void onSendDataSuccess(byte[] data) {
                runOnUiThread(() -> tvReceived.append("Sent: " + new String(data) + "\n"));
            }

            @Override
            public void onSendDataError(byte[] data, String errorMsg) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Send failed: " + errorMsg, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onReceiveDataSuccess(byte[] buffer) {
                String message = new String(buffer);
                handler.obtainMessage(0, message).sendToTarget();
                if(message.equals(DISCONNECT_MESSAGE)){
                    disconnect();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onReceiveDataError(String errorMsg) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Receive failed: " + errorMsg, Toast.LENGTH_SHORT).show());
            }
        });
        connectedThread.start();
    }

    private void sendMessage() {
        String message = etMessage.getText().toString();
        if (!message.isEmpty() && connectedThread != null) {
            connectedThread.write(message.getBytes());
        } else {
            Toast.makeText(this, "Message is empty or not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnect() {
        closeConnectThread();
        closeConnectedThread();
        closeBTSocket();
        restartBTSocketServer();
    }

    private void closeConnectThread(){
        // 关闭连接线程
        if (connectThread != null) {
            connectedThread.write(DISCONNECT_MESSAGE.getBytes());
            connectThread.cancel();
            connectThread = null;
        }
    }

    private void closeConnectedThread(){
        // 关闭通信线程
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    private void closeBTSocket(){
        // 关闭蓝牙套接字
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing bluetooth socket", e);
            }
            bluetoothSocket = null;
        }
    }

    private void restartBTSocketServer(){
        // 重新启动ServerThread
        if (serverThread != null) {
            serverThread.cancel();
            serverThread = null;
        }
        startServerThread();
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        unregisterReceiver(receiver);
        disconnect();
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                @SuppressLint("MissingPermission") String deviceName = device.getName();
                if (deviceName != null) {
                    String deviceAddress = device.getAddress();
                    deviceArrayAdapter.add(deviceName + "\n" + deviceAddress);
                    bluetoothDevices.add(device);
                }
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                searchBluetoothDevices();
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth scanning", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void searchBluetoothDevices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
        } else {
            deviceArrayAdapter.clear();
            bluetoothDevices.clear();
            bluetoothAdapter.startDiscovery();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Bluetooth Device");
            ListView deviceListView = new ListView(this);
            deviceListView.setAdapter(deviceArrayAdapter);
            builder.setView(deviceListView);
            AlertDialog dialog = builder.create();
            dialog.show();

            deviceListView.setOnItemClickListener((parent, view, position, id) -> {
                BluetoothDevice device = bluetoothDevices.get(position);
                connectToDevice(device);
                dialog.dismiss();
            });
        }
    }
}