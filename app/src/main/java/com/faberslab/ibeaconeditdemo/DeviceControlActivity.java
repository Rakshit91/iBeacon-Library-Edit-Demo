/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.faberslab.ibeaconeditdemo;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.faberslab.ibeacon.BluetoothLeService;

import java.util.ArrayList;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BluetoothLeService}, which in
 * turn interacts with the Bluetooth LE API.
 */
public class DeviceControlActivity extends AppCompatActivity {
    private final static String TAG = "DeviceControlActivity";

    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private TextView text_state;
    private EditText text_uuid;
    private EditText text_Major;
    private EditText text_Minor;
    private TextView text_Period;
    private EditText password;
    private EditText deviceName;
    private Spinner spinner;

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;

    private Button but_enable;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        // the service is created and ready
        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            // get service refernce
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
                    .getService();
            // initialise service
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            Log.i(TAG, "onServiceConnected: connected");
            // connect to the iBeacon via its mac address
            mBluetoothLeService.connect(mDeviceAddress);
        }

        // the service has stopped
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }

    };

    //     Handles various events fired by the Service.
    //     GATT_CONNECTED_ACTION: connected to a GATT server.
    //     ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    //     GATT_SERVICES_DISCOVERED_ACTION: discovered GATT services.
    //     ACTION_DATA_AVAILABLE: received data from the device. This can be a
    //     result of read
    //     or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // the iBeacon is now connected
            if (BluetoothLeService.GATT_CONNECTED_ACTION.equals(action)) {
                text_state.setText("connected");
                Toast.makeText(DeviceControlActivity.this, "connected", Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED // the iBeacon got disconnected
                    .equals(action)) {
                invalidateOptionsMenu();
                Toast.makeText(DeviceControlActivity.this, "disconnected", Toast.LENGTH_SHORT).show();
                text_state.setText("disconnected");
            } else if (BluetoothLeService.GATT_SERVICES_DISCOVERED_ACTION // the iBeacon is now ready for interaction
                    .equals(action)) {
            } else if (BluetoothLeService.EXTRA_UUID_DATA_ACTION.equals(action)) { // iBeacon UUID received
                String uuid = intent
                        .getStringExtra(BluetoothLeService.UUID_DATA);
                Log.i(TAG, "uuid=" + uuid);
                text_uuid.setText(uuid);
            } else
                // other data like major, minor, broadcast interval and txpower are received
                if (BluetoothLeService.EXTRA_OTHER_DATA_ACTION
                        .equals(action)) {
                    int Major = intent.getIntExtra(BluetoothLeService.MAJOR_DATA,
                            -1);
                    int Minor = intent.getIntExtra(BluetoothLeService.MINOR_DATA,
                            -1);
                    int Period = intent.getIntExtra(BluetoothLeService.PERIOD_DATA,
                            -1);
                    int txPower = intent.getIntExtra(
                            BluetoothLeService.TXPOWER_DATA, -1);
                    txPowerTemp = txPower;
                    text_Major.setText(Major + "");
                    text_Minor.setText(Minor + "");
                    text_Period.setText(Period + "");
                    Log.i(TAG, Minor + Major + Period + "OTHER");
                    switch (txPower) {
                        case 0:
                            spinner.setSelection(0);
                            break;
                        case 1:
                            spinner.setSelection(1);
                            break;
                        case 2:
                            spinner.setSelection(2);
                            break;
                        case 3:
                            spinner.setSelection(3);
                            break;
                    }
                } else if (BluetoothLeService.ACTION_EXTRA_PASSWORD_MISTAKE // incorrect password
                        .equals(action)) {
                    Message message = handler.obtainMessage();
                    message.what = 0;
                    message.sendToTarget();
                } else if (BluetoothLeService.ACTION_EXTRA_PASSWORD_RIGHT // password accepted
                        .equals(action)) {
                    Message message = handler.obtainMessage();
                    message.what = 1;
                    message.sendToTarget();
                } else if (BluetoothLeService.NAME_AVAILABLE_ACTION.equals(action)) { // name of iBeacon received
                    mDeviceName = intent.getStringExtra(BluetoothLeService.NAME_DATA);
                    deviceName.setText(mDeviceName);
                    setTitle(mDeviceName);
                }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modify_ibeacon);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        text_state = (TextView) findViewById(R.id.text_state);
        text_uuid = (EditText) findViewById(R.id.text_uuid);
        text_Major = (EditText) findViewById(R.id.text_Major);
        text_Minor = (EditText) findViewById(R.id.text_Minor);
        text_Period = (TextView) findViewById(R.id.text_Period);
        password = (EditText) findViewById(R.id.text_Password);
        deviceName = (EditText) findViewById(R.id.text_Name);
        spinner = (Spinner) findViewById(R.id.spinner);

        // values for txpower
        List<String> list = new ArrayList<>();
        list.add("-23");
        list.add("-6");
        list.add("0");
        list.add("4");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                                       int arg2, long arg3) {
                switch (arg2) {
                    case 0:
                        txPowerTemp = 0;
                        break;

                    case 1:
                        txPowerTemp = 1;
                        break;
                    case 2:
                        txPowerTemp = 2;
                        break;
                    case 3:
                        txPowerTemp = 3;
                        break;
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub

            }
        });

        but_enable = (Button) findViewById(R.id.but_enable);
        but_enable.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                validatePassword();
            }
        });
        setTitle(mDeviceName);
        // Create service intent
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        // start service
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    private int txPowerTemp;

    @Override
    protected void onResume() {
        super.onResume();
        // register broadcast receiver to get information received from the iBeacon via service
        registerReceiver(mGattUpdateReceiver,
                BluetoothLeService.makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            //connect if disconnected
            boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister receiver
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // disconnect the service
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    public void validatePassword() {
        // send password to ble device to get it verified
        mBluetoothLeService.validatePassword(password.getText().toString());
    }

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    // incorrect password
                    Toast.makeText(DeviceControlActivity.this, "error password",
                            Toast.LENGTH_SHORT).show();
                    break;

                case 1:
                    // password accepted
                    passwordVerified();

                    break;
            }
        }
    };

    private void passwordVerified() {
        // write the UUID to the iBeacon
        mBluetoothLeService.modifyUUID(text_uuid.getText().toString());
        // write other data to the iBeacon
        mBluetoothLeService.modifyOtherParameter(text_Major.getText()
                        .toString(), text_Minor.getText().toString(),
                text_Period.getText().toString(), txPowerTemp);
        if (!mDeviceName.equals(deviceName.getText().toString())) {
            // write name the the iBeacon
            mBluetoothLeService.modifyDeviceName(deviceName.getText()
                    .toString());
        }
        // disconnect iBeacon
        mBluetoothLeService.closeBLE();
        Toast.makeText(DeviceControlActivity.this, "Modified successfully", Toast.LENGTH_SHORT).show();
        // go back to previous screen
        finish();

    }
}
