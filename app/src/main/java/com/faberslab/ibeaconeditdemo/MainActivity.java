package com.faberslab.ibeaconeditdemo;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.faberslab.ibeacon.BluetoothLeScanService;
import com.faberslab.ibeacon.iBeaconClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private ListView mDevicesListView;
    private static final int REQUEST_ENABLE_BT = 1;
    private Intent intent;
    private boolean mScanning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * Check if the current mobile phone supports ble Bluetooth, if you do
         * not support the exit program
         */
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE is not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        /**
         * Adapter Bluetooth, get a reference to the Bluetooth adapter (API),
         * which must be above android4.3 or above.
         */
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        /**
         * Check whether the device supports Bluetooth
         */
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Blue not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        //initialise list adapter
        mLeDeviceListAdapter = new LeDeviceListAdapter();

        mDevicesListView = (ListView) findViewById(R.id.devices_list_view);
        mDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                onListItemClick(i);
            }
        });

        //pass the adapter to the list view
        mDevicesListView.setAdapter(mLeDeviceListAdapter);

        //create service intent, later used to start/stop the service
        intent = new Intent(this, BluetoothLeScanService.class);

        //devices with marshmallow and above needs location service on to perform BLE scanning
        if (Build.VERSION.SDK_INT < 23) {
            // start scanning service
            startService(intent);
        } else {
            // check and ask for enabling location sevice
            checkLocationPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // register broadcast to received scanned devices sent by scanning service
        LocalBroadcastManager.getInstance(this).registerReceiver(mDeviceDiscoveredBroadcastReceiver, new IntentFilter(BluetoothLeScanService.DEVICE_DISCOVERY_BROADCAST));

        /**
         * In order to ensure that the device can be used in Bluetooth, if the
         * current Bluetooth device is not enabled, the pop-up dialog box to the
         * user to grant permissions to enable
         */
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        //devices with marshmallow and above needs location service on to perform BLE scanning
        if (Build.VERSION.SDK_INT >= 23) {
            if (!checkLocationPermission()) {
                return;
            }
        }

        // on pause we had stopped scanning to save battery so
        // we need to start ask the service to start the scanning again
        scanLeDevice(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //ask service to stop the scanning to reduce battery drainage
        scanLeDevice(false);
        // unregister broadcast receiver as we have stopped
        // the service that is sending these broadcasts
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mDeviceDiscoveredBroadcastReceiver);
    }

    // ---------scan BLE start stop-----------------------------------------
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mScanning = true;
            Intent intent = new Intent(BluetoothLeScanService.SCAN_COMMAND_ACTION);
            intent.putExtra(BluetoothLeScanService.EXTRA_SCAN_COMMAND_CODE, BluetoothLeScanService.SCAN_COMMAND_START);
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
        } else {
            mScanning = false;
            Intent intent = new Intent(BluetoothLeScanService.SCAN_COMMAND_ACTION);
            intent.putExtra(BluetoothLeScanService.EXTRA_SCAN_COMMAND_CODE, BluetoothLeScanService.SCAN_COMMAND_STOP);
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
        }
    }

    // force user to enable location service
    private boolean checkLocationPermission() {

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationRequestDialogFragment dialogFragment = LocationRequestDialogFragment.newInstance("", "");
            dialogFragment.setOnFragmentInteractionListener(new LocationRequestDialogFragment.OnFragmentInteractionListener() {
                @Override
                public void locationServiceEnabled() {
                    startService(intent);
                }
            });
            dialogFragment.setCancelable(false);
            dialogFragment.show(getSupportFragmentManager(), "");
            return false;
        } else {
            startService(intent);
            return true;
        }

    }

    // this broadcast receiver will receive the ble devices as they are scanned by the service
    BroadcastReceiver mDeviceDiscoveredBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final iBeaconClass.iBeacon ibeacon = (iBeaconClass.iBeacon) intent.getSerializableExtra(BluetoothLeScanService.EXTRA_IBEACON);
            Log.i("MainActivity", "iBeacon Received");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(ibeacon);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    // ----- Adapter for holding devices found through scanning.-------------
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<iBeaconClass.iBeacon> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public iBeaconClass.iBeacon getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        /**
         * Add data,And sort by RSSI
         *
         * @param device
         */
        public void addDevice(iBeaconClass.iBeacon device) {
            if (device == null)
                return;
            Log.i("LeDeviceListAdapter", "iBeacon not null");
            for (int i = 0; i < mLeDevices.size(); i++) {
                String btAddress = mLeDevices.get(i).bluetoothAddress;
                if (btAddress.equals(device.bluetoothAddress)) {
                    mLeDevices.remove(i);
                    mLeDevices.add(i, device);
                    Log.i("LeDeviceListAdapter", "iBeacon replaced");
                    return;
                }
            }
            Log.i("LeDeviceListAdapter", "adding new iBeacon");
            mLeDevices.add(device);
            Collections.sort(mLeDevices, new Comparator<iBeaconClass.iBeacon>() {
                @Override
                public int compare(iBeaconClass.iBeacon h1, iBeaconClass.iBeacon h2) {
                    return h2.rssi - h1.rssi;
                }
            });
        }

        class ViewHolder {
            TextView txt_name;
            TextView txt_mac;
            TextView txt_rssi;

        }


        @SuppressLint("InflateParams")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.item_ibeacon, null);
                viewHolder = new ViewHolder();
                viewHolder.txt_name = (TextView) view.findViewById(R.id.txt_name_value);
                viewHolder.txt_mac = (TextView) view.findViewById(R.id.txt_mac_value);
                viewHolder.txt_rssi = (TextView) view.findViewById(R.id.txt_rssi);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }
            iBeaconClass.iBeacon device = mLeDevices.get(i);
            final String deviceName = device.name;
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.txt_name.setText(" : " + device.name);
            else
                viewHolder.txt_name.setText(" :" + "Unknown  device");

            viewHolder.txt_rssi.setText("RSSI : " + device.rssi + " ");
            viewHolder.txt_mac.setText(" : " + device.bluetoothAddress);

            return view;
        }
    }

    protected void onListItemClick(int position) {
        iBeaconClass.iBeacon device = mLeDeviceListAdapter.getDevice(position);
        if (device == null)
            return;

        if (!device.name.equals("pBeacon_n")) {
            Toast.makeText(MainActivity.this, "The iBeacon is not connectable", Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, DeviceControlActivity.class);

        Bundle bundle = new Bundle();
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.bluetoothAddress);
        intent.putExtras(bundle);

        if (mScanning) {
            // ask scan service to stop scanning
            Intent intent1 = new Intent(BluetoothLeScanService.SCAN_COMMAND_ACTION);
            intent1.putExtra(BluetoothLeScanService.SCAN_COMMAND_ACTION, BluetoothLeScanService.SCAN_COMMAND_STOP);
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent1);
            mScanning = false;
        }
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        //stop BLE scan service
        stopService(intent);
        super.onDestroy();
    }

}
