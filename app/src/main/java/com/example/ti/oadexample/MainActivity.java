package com.example.ti.oadexample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // Locals
    private boolean mScanning = false;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings mScanSettings;
    private BluetoothGatt mBluetoothGatt;
    private ArrayList<ScanFilter> mScanFilters = new ArrayList<>();
    private Map<BluetoothDevice, Integer> mBtDevices = new HashMap<>();
    private TableLayout mTableDevices;

    // Bluetooth SIG identifiers
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Main Activity Object
    public static Activity activity = null;

    // Tag used for logging
    private static final String TAG = "MainActivity";

    // Request codes
    private final static int MY_PERMISSIONS_REQUEST_ENABLE_BT = 1;
    private final static int MY_PERMISSIONS_REQUEST_MULTIPLE= 2;

    // Intent actions
    public final static String ACTION_GATT_CONNECTED =
            "com.example.ti.oadexample.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.ti.oadexample.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.ti.oadexample.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_NOTIFY = "com.example.ti.oadexample.ACTION_DATA_NOTIFY";
    public final static String ACTION_DATA_READ = "com.example.ti.oadexample.ACTION_DATA_READ";
    public final static String ACTION_DATA_WRITE = "com.example.ti.oadexample.ACTION_DATA_WRITE";

    // Intent extras
    public final static String EXTRA_DATA = "com.example.ti.odaexample.EXTRA_DATA";
    public final static String EXTRA_UUID = "com.example.ti.odaexample.EXTRA_UUID";

    // Que system for descriptor and characteristic actions;
    private Queue<BleRequest> characteristicQueue = new LinkedList<>();
    public enum BleRequestOperation {
        write,
        read,
        enableNotification,
    }
    public class BleRequest {
        public int id;
        public BluetoothGattDescriptor descriptor;
        public BluetoothGattCharacteristic characteristic;
        public BleRequestOperation operation;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        activity = this;

        // Get UI elements
        mTableDevices = (TableLayout) findViewById(R.id.devicesFound);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        //  For Android M: Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            int storageAccess = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
            int locationAccess = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);

            // App needs location permission for BLE scanning, and external storage access for
            // loading OAD images
            if((locationAccess != PackageManager.PERMISSION_GRANTED) ||
                    (storageAccess != PackageManager.PERMISSION_GRANTED)){
                requestPermissions(new String[]{
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                                MY_PERMISSIONS_REQUEST_MULTIPLE);

            }
        }

        // Configure default scan filter
        ScanFilter filter = new ScanFilter.Builder().build();
        mScanFilters.add(filter);

        // Configure default scan settings
        mScanSettings = new ScanSettings.Builder().build();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Bluetooth is disabled or not available. Display
            // a dialog requesting user permission to enable Bluetooth.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, MY_PERMISSIONS_REQUEST_ENABLE_BT);
        } else if (!mScanning) {
            // Start scanning
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            scanLeDevice(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mScanning) {
            // Stop scanning
            scanLeDevice(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        // Callback have been received from a permission request
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_MULTIPLE:
                if((permissions[0] == Manifest.permission.ACCESS_COARSE_LOCATION )
                    && (grantResults[0] != PackageManager.PERMISSION_GRANTED)) {

                    // Access location was not granted. Display a warning.
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not display any bluetooth scan results.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                if((permissions.length > 1) && (permissions[1] == Manifest.permission.READ_EXTERNAL_STORAGE )
                        && (grantResults[1] != PackageManager.PERMISSION_GRANTED)) {

                    // External storage access was not granted. Display a warning.
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("The app won't be able to load an OAD image, since external storage access not has been granted.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MY_PERMISSIONS_REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                // Bluetooth was not enabled, end activity
                finish();
                return;
            }
        }
    }

    /**
     * Connect to a bluetooth device. The connection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     *
     * @param btDevice instance of device to connect to.
     */
    public void connectToDevice(BluetoothDevice btDevice) {

        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }

        mBluetoothGatt = btDevice.connectGatt(this, false, mGattCallback);

        // Stop scanning
        if (mScanning) {
            scanLeDevice(false);
        }
    }

    /**
     * Disconnect from a device. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            if (Util.WARN) Log.w(TAG, "Bluetooth not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * Retrieve a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null){
            return null;
        }

        return mBluetoothGatt.getServices();
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the
     * {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            if (Util.WARN) Log.w(TAG, "Bluetooth not initialized");
            return;
        }

        // Queue the characteristic to read
        BleRequest req = new BleRequest();
        req.characteristic = characteristic;
        req.operation = BleRequestOperation.read;
        characteristicQueue.add(req);

        // If there is only 1 item in the queue, then read it. If more than 1, it is handled
        // asynchronously in the callback
        if((characteristicQueue.size() == 1)) {
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    /**
     * Request a write on a given {@code BluetoothGattCharacteristic}. The write result is reported
     * asynchronously through the
     * {@code BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to write to.
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            if (Util.WARN) Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        // Queue the characteristic to write
        BleRequest req = new BleRequest();
        req.characteristic = characteristic;
        req.operation = BleRequestOperation.write;
        characteristicQueue.add(req);

        // If there is only 1 item in the queue, then write it. If more than 1, it is handled
        // asynchronously in the callback
        if((characteristicQueue.size() == 1)) {
            mBluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    /**
     * Request a write on a given {@code BluetoothGattCharacteristic} immediately. The write result is reported
     * asynchronously through the
     * {@code BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to write to.
     */
    public void writeCharacteristicNoResponse(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            if (Util.WARN) Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Scan for bluetooth devices
     *
     * @param enable Set to true to start scanning or to false to stop scanning
     */
    private void scanLeDevice(final boolean enable) {

        if(mLEScanner == null) {
            if (Util.WARN) Log.w(TAG, "Could not get LEScanner object");
            return;
        }

        if (enable) {
            // Clear list of scanned devices
            mBtDevices.clear();
            mScanning = true;
            mLEScanner.startScan(mScanFilters, mScanSettings, mScanCallback);
        } else {
            mScanning = false;
            mLEScanner.stopScan(mScanCallback);
        }
    }

    /**
     * Enable or disable notification on a given characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enable If true, enable notification. Otherwise, disable it.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enable) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            if (Util.WARN) Log.w(TAG, "Bluetooth not initialized");
            return;
        }
        // Enable/disable notification
        boolean status = mBluetoothGatt.setCharacteristicNotification(characteristic, enable);
        if(status == false){
            if (Util.WARN) Log.w(TAG, "Set notification failed");
            return;
        }

        // Write descriptor for notification
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
        if(descriptor != null)
        {
            descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00, 0x00});
            writeGattDescriptor(descriptor);
        }
    }

    /**
     * Set the connection priority.
     *
     * @param connectionPriority
     * @return bool whether the call was successful
     */
    public boolean requestConnectionPriority(int connectionPriority) {
        return this.mBluetoothGatt.requestConnectionPriority(connectionPriority);
    }

    /**
     * Bluetooth gatt callback function
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if(Util.INFO) Log.i(TAG, "onConnectionStateChange. Status: " + status);
            String intentAction;

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    intentAction = ACTION_GATT_CONNECTED;
                    broadcastUpdate(intentAction);
                    if(Util.INFO) Log.i(TAG, "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    if(Util.INFO) Log.i(TAG, "STATE_DISCONNECTED");
                    intentAction = ACTION_GATT_DISCONNECTED;
                    broadcastUpdate(intentAction);
                    // Close connection completely after disconnect, to be able
                    // to start clean.
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                    }
                    break;
                default:
                    if (Util.ERROR) Log.e(TAG, "STATE_OTHER");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i(TAG, "onServicesDiscovered: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Broadcast that services has successfully been discovered
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                if (Util.WARN) Log.w(TAG, "onServicesDiscovered received with error: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_NOTIFY, characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         int status) {

            // Read action has finished, remove from queue
            characteristicQueue.remove();

            // Broadcast the result
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_READ, characteristic);
            }
            else{
                if (Util.ERROR) Log.e(TAG, "onCharacteristicRead error: " + status);
            }

            // Handle the next element from the queue
            if(characteristicQueue.size() > 0){
                BleRequest req = characteristicQueue.element();
                switch(req.operation)
                {
                    case write:
                        mBluetoothGatt.writeCharacteristic(req.characteristic);
                        break;
                    case read:
                        mBluetoothGatt.readCharacteristic(req.characteristic);
                        break;
                    case enableNotification:
                        mBluetoothGatt.writeDescriptor(req.descriptor);
                        break;
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (Util.ERROR) Log.e(TAG, "Callback: Error writing GATT Descriptor: "+ status);
            }

            // Write finished, remove from queue
            characteristicQueue.remove();

            // Continue handling items if there is more in the queue
            if(characteristicQueue.size() > 0){
                BleRequest req = characteristicQueue.element();
                switch(req.operation)
                {
                    case write:
                        mBluetoothGatt.writeCharacteristic(req.characteristic);
                        break;
                    case read:
                        mBluetoothGatt.readCharacteristic(req.characteristic);
                        break;
                    case enableNotification:
                        mBluetoothGatt.writeDescriptor(req.descriptor);
                        break;
                }
            }

        };

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {

            if (Util.INFO) Log.i(TAG, "onCharacteristicWrite: "+ status);

            // Broadcast the result
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_WRITE, characteristic);
            }
            else {
                if (Util.WARN) Log.w(TAG, "Write failed");
            }

            // Handle the queue if used
            if((characteristicQueue.size() > 0)) {

                // Write action has finished, remove from queue
                characteristicQueue.remove();

                // Handle the next element from the queue
                if (characteristicQueue.size() > 0) {
                    BleRequest req = characteristicQueue.element();
                    switch (req.operation) {
                        case write:
                            mBluetoothGatt.writeCharacteristic(req.characteristic);
                            break;
                        case read:
                            mBluetoothGatt.readCharacteristic(req.characteristic);
                            break;
                        case enableNotification:
                            mBluetoothGatt.writeDescriptor(req.descriptor);
                            break;
                    }
                }
            }
        }
    };

    /**
     * Device scan callback
     */
    private ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            final BluetoothDevice btDevice = result.getDevice();
            if (btDevice == null){
                if (Util.ERROR) Log.e("ScanCallback", "Could not get bluetooth device");
                return;
            }

            // Check if device already added to list of scanned devices
            String macAddress = btDevice.getAddress();
            for(BluetoothDevice dev : mBtDevices.keySet())
            {
                // Device already added, do nothing
                if(dev.getAddress().equals(macAddress) ){
                    return;
                }
            }

            // Add device to list of scanned devices
            mBtDevices.put(btDevice, result.getRssi());

            // Update the device table with the new device
            updateDeviceTable();
        }
    };

    /**
     * Update the table view displaying all scanned devices.
     * This function will clean the current table view and re-add all items that has been scanned
     */
    private void updateDeviceTable() {

        // Clean current table view
        mTableDevices.removeAllViews();

        for(final BluetoothDevice savedDevice : mBtDevices.keySet()) {

            // Get RSSI of this device
            int rssi = mBtDevices.get(savedDevice);

            // Create a new row
            final TableRow tr = new TableRow(MainActivity.this);
            tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));

            // Add Text view for rssi
            TextView tvRssi = new TextView(MainActivity.this);
            tvRssi.setText(Integer.toString(rssi));
            TableRow.LayoutParams params = new TableRow.LayoutParams(0);
            params.setMargins(20,0,20,0);
            tvRssi.setLayoutParams(params);

            // Add Text view for device, displaying name and address
            TextView tvDevice = new TextView(MainActivity.this);
            String deviceName = savedDevice.getName();
            if(deviceName == null){
                deviceName ="";
            }
            tvDevice.setText(deviceName + "\r\n" + savedDevice.getAddress());
            tvDevice.setLayoutParams(new TableRow.LayoutParams(1));

            // Add a connect button to the right
            Button b = new Button(MainActivity.this);
            b.setText(R.string.button_connect);
            b.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

            // Create action when clicking the connect button
            b.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {

                    // Create the activity for the selected device
                    final Intent intent = new Intent(MainActivity.this, FwUpdateActivity.class);
                    intent.putExtra(FwUpdateActivity.EXTRAS_DEVICE_NAME, savedDevice.getName());

                    // Connect to device
                    connectToDevice(savedDevice);

                    // start activity
                    startActivity(intent);
                }
            });

            // Add items to the row
            tr.addView(tvRssi);
            tr.addView(tvDevice);
            tr.addView(b);
            tr.setGravity(Gravity.CENTER);

            // Add row to the table layout
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTableDevices.addView(tr);
                }
            });
        }
    }

    /**
     * Broadcast an update on the specified action
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /**
     * Broadcast an update on the specified action
     */
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {

        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());
        intent.putExtra(EXTRA_DATA, characteristic.getValue());
        sendBroadcast(intent);
    }

    /**
     * Write gatt descriptor if queue is ready.
     */
    private void writeGattDescriptor(BluetoothGattDescriptor d){
        // Add descriptor to the write queue
        BleRequest req = new BleRequest();
        req.descriptor = d;
        req.operation = BleRequestOperation.enableNotification;
        characteristicQueue.add(req);
        // If there is only 1 item in the queue, then write it. If more than 1, it will be handled
        // in the onDescriptorWrite callback
        if(characteristicQueue.size() == 1){
            mBluetoothGatt.writeDescriptor(d);
        }
    }


}
