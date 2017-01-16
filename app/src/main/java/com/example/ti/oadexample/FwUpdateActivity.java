package com.example.ti.oadexample;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class FwUpdateActivity extends AppCompatActivity {

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";

    // Locals
    private String mDeviceName;
    private MainActivity mMainActivity;
    private static final short MIN_BLOCK_DELAY = 10; // make sure block delay is larger than connection interval

    // Tag used for logging
    private static final String TAG = "FwUpdateActivity";

    // GUI
    private TextView mUpdateImage;
    private TextView mConnectionState;
    private TextView mLog;
    private Button mBtnProgram;
    private Button mBtnSelectImage;
    private ProgressBar mProgressBar;
    private TextView mProgressInfo;
    private SeekBar mSbDelay;
    private TextView mTvDelay;

    // BLE
    private HashMap<String, BluetoothGattCharacteristic> mGattCharacteristics = new HashMap<>();
    public BluetoothGattCharacteristic mCharIdentify = null;
    public BluetoothGattCharacteristic mCharBlock = null;
    private BluetoothGattCharacteristic mCharConnReq = null;
    private BluetoothGattCharacteristic mCharTestResult = null;
    public BluetoothGattCharacteristic mCharImageStatus = null;
    public BluetoothGattCharacteristic getCharConnReq(){
        return mCharConnReq;
    }

    // Programming
    public int mBlockDelay = 0;

    // Housekeeping
    boolean mIsConnected = false;
    private boolean mTestOK = false;
    private boolean mOadFailedAlertShown = false;
    boolean mSafeMode = false;

    // Request code for file activity
    public static final int FILE_ACTIVITY_REQUEST = 0;

    // Class object to handle the OAD process
    OadProcess mOadProcess = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fw_update);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Add back-button to parent activity
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Get name of device that this intent is opened for
        Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);

        // Set activity title to the device name
        setTitle(mDeviceName);

        // Get UI elements
        mProgressInfo = (TextView) findViewById(R.id.tv_info);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mUpdateImage = (TextView) findViewById(R.id.tv_new_image);
        mLog = (TextView) findViewById(R.id.tv_log);
        mBtnProgram = (Button) findViewById(R.id.btn_program);
        mBtnSelectImage = (Button) findViewById(R.id.btn_selectImage);
        mProgressBar = (ProgressBar) findViewById(R.id.pb_progress);
        mSbDelay = (SeekBar) findViewById(R.id.sbDelay);
        mTvDelay = (TextView) findViewById(R.id.tvDelay);

        // Initialize UI elements
        mBtnProgram.setEnabled(false);
        mBtnSelectImage.setEnabled(false);
        mUpdateImage.setText("");
        mTvDelay.setText(getDelayText());
        mBlockDelay = mSbDelay.getProgress() + MIN_BLOCK_DELAY;
        mSbDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mBlockDelay = progress + MIN_BLOCK_DELAY; // adding offset because delay must be larger than connection interval
                mTvDelay.setText(String.valueOf(mBlockDelay) + " ms");
            }


            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mLog.setMovementMethod(new ScrollingMovementMethod());

        // Get instance of main activity
        mMainActivity = (MainActivity) MainActivity.activity;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register a receiver for broadcast updates
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Do not receive broadcast updates when paused
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMainActivity = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Disconnect when home button is pressed
                Toast.makeText(getApplicationContext(),"Disconnecting", Toast.LENGTH_LONG).show();
                mMainActivity.disconnect();
                finish();
                break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we are responding to
        if (requestCode == FILE_ACTIVITY_REQUEST) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                String filename = data.getStringExtra(FileActivity.EXTRA_FILENAME);
                loadFile(filename);
            }
        }
    }

    /**
     * Method to create the text field for the delay slider
     * @return text to add next to the slider
     */
    public String getDelayText(){
        return String.valueOf(mSbDelay.getProgress() + MIN_BLOCK_DELAY) + " ms";
    }

    /**
     * Function called when the checkbox for selecting safe mode is clicked
     */
    public void onSafeMode(View view) {

        // Is the safe mode selected?
        mSafeMode = ((CheckBox) view).isChecked();

        // Disable block delay slider if safe mode selected
        mSbDelay.setEnabled(!mSafeMode);
        if(mSafeMode) {
            mTvDelay.setText("N/A");
        }
        else{
            mTvDelay.setText(getDelayText());
        }
    }


    /**
     * Function called when the user clicks on the select image button
     */
    public void onSelectImage(View v) {
        Intent i = new Intent(this, FileActivity.class);
        startActivityForResult(i, FILE_ACTIVITY_REQUEST);
    }

    /**
     * Function called when user clicks the program/cancel button
     *
     * @param v
     */
    public void onProgramImage(View v) {
        if (isProgramming()) {
            mOadProcess.stopProgramming();
        } else {
            // Display warning if not connected
            if(!mIsConnected)
            {
                Toast.makeText(getApplicationContext(),"Device not connect. Please try to re-connect.", Toast.LENGTH_LONG).show();
                return;
            }
            // Start programming
            mOadFailedAlertShown = false;
            mOadProcess.startProgramming();
        }
    }

    /**
     * Update GUI based on programming status
     */
    public void updateGui(final boolean programming) {

        FwUpdateActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (programming) {
                    // Busy: show cancel button, disable file selector
                    mBtnProgram.setText(R.string.cancel);
                    mBtnSelectImage.setEnabled(false);
                } else {
                    // Idle: no progress, show program button, enable file selector if connected
                    mProgressBar.setProgress(0);
                    mBtnProgram.setText(R.string.start_prog);
                    mBtnSelectImage.setEnabled(true);
                }


            }
        });

    }

    /**
     * Display progress info
     */
    public void displayProgressText(String txt)
    {
        mProgressInfo.setText(txt);
    }

    /**
     * Set progress bar value
     */
    public void updateProgressBar(int progress)
    {
        mProgressBar.setProgress(progress);
    }

    /**
     * Append/Set log text
     *
     * @param txt    Text to write to log window
     * @param append If true, the text will be appended to existing test, otherwise
     *               it old text will be overridden.
     */
    public void log(CharSequence txt, boolean append){
        if(append){
            mLog.append(txt);
        }
        else{
            mLog.setText(txt);
        }
    }

    /**
     *  Check if programming is in progress
     */
    private boolean isProgramming() {
        if ((mOadProcess != null) && mOadProcess.isProgramming()){
            return true;
        }
        return false;
    }

    /**
     * Function called when the user has chosen a file
     */
    private boolean loadFile(String filepath) {
        int readLen = 0;

        // Check self test result
        if (mTestOK != true) {
            AlertDialog.Builder testFailedAlertDialog = new AlertDialog.Builder(this);
            testFailedAlertDialog.setTitle("Error");
            testFailedAlertDialog.setMessage("External FLASH self test failed, cannot do OAD on this device, If the device is connected to a debugger remove debugger, remove and insert battery.");
            testFailedAlertDialog.setNegativeButton("OK", null);
            (testFailedAlertDialog.create()).show();
            return false;
        }

        // Create OAD process object
        if(mOadProcess == null)
        {
            mOadProcess = OadProcess.newInstance(mMainActivity, this);
        }

        // Read file
        readLen = mOadProcess.readFile(filepath);
        if(readLen == -1){
            mLog.setText("Failed to read image : " + filepath + "\n");
            return false;
        }

        // Configure connection parameters
        if ( Build.VERSION.SDK_INT >= 21)
        {
            if (Util.DEBUG) Log.d(TAG,"Requested connection priority HIGH, result : " + mMainActivity.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH));
        }
        mOadProcess.setConnectionParameters();

        // Update GUI elements
        mUpdateImage.setText(filepath);
        mBtnProgram.setEnabled(true);
        updateGui(isProgramming());

        // Log
        mLog.setText("Image to program : " + filepath + "\n");
        mLog.append("File size : " + readLen + " bytes (" + (readLen / 16) + ") blocks\n");
        mLog.append("Ready to program device!\n");

        return true;
    }

    /**
     * Update the connection status field
     */
    private void updateConnectionState(final int resourceId) {
        FwUpdateActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);

                // Enable image selection when connected
                if(mIsConnected){
                    mBtnSelectImage.setEnabled(true);
                }
            }
        });
    }


    private void updateFlashSelfTestState(final byte[] value) {
        FwUpdateActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((value[0] & 0xFF) == 0x01) {
                    mLog.append(Html.fromHtml(String.format("<font color=#00CC00>FLASH Self test passed !</font>")));
                    mTestOK = true;
                }
                else {
                    mLog.append(Html.fromHtml(String.format("<font color=#CC0000>FLASH Self test failed !</font>")));
                    mTestOK = false;
                }
            }
        });
    }

    /**
     * Create an intent filter for actions broadcast
     * by MainActivity
     *
     * @return The created IntentFilter object
     */
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MainActivity.ACTION_GATT_CONNECTED);
        intentFilter.addAction(MainActivity.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(MainActivity.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(MainActivity.ACTION_DATA_NOTIFY);
        intentFilter.addAction(MainActivity.ACTION_DATA_READ);
        return intentFilter;
    }

    /**
     * Iterate through the supported GATT Services/Characteristics,
     * and initialize UI elements displaying them.
     */
    private void initializeGattServiceUIElements(final Context context, List<BluetoothGattService> gattServices) {

        // Loop through services
        boolean serviceFound = false;
        for (BluetoothGattService s : gattServices) {
            if(TIOADProfile.isOadService(s))
            {
                // OAD service found
                if (Util.INFO) Log.i(TAG,"Found TI OAD Service");
                serviceFound = true;

                // Save characteristics
                mCharIdentify = s.getCharacteristic(UUID.fromString(TIOADProfile.OAD_IMAGE_NOTIFY_CHAR));
                mCharBlock = s.getCharacteristic(UUID.fromString(TIOADProfile.OAD_BLOCK_REQUEST_CHAR));
                mCharBlock.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                mCharImageStatus = s.getCharacteristic(UUID.fromString(TIOADProfile.OAD_IMAGE_STATUS_CHAR));
            }
            else if (TIOADProfile.isConnectionControlService(s)) {
                mCharConnReq = s.getCharacteristic(UUID.fromString(TIOADProfile.REQUEST_CONNECTION_PARAMETERS_CHAR));
            }
            else if(TIOADProfile.isTestService(s)){
                // Test service found, save characteristics
                mCharTestResult = s.getCharacteristic(UUID.fromString(TIOADProfile.TEST_DATA_CHAR));

                // Read test result to check if external flash is ok
                mMainActivity.readCharacteristic(mCharTestResult);
            }
        }

        // Check if self test result is present
        if (mCharTestResult == null) {
            // Test service is missing, so we cannot check, print a warning in the text field.
            mLog.append(Html.fromHtml(String.format("<font color=#CC0000>No test service on current device, cannot check if external flash is working !</font>")));
            mTestOK = true;
        }

        // Verify that OAD service was found
        if(!serviceFound)
        {
            FwUpdateActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    // Servie is missing, display an error
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Error!");
                    builder.setMessage("The expected OAD service was not discovered for current device. " +
                            "The device will be disconnected.");
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // Disconnect and return from activity
                            mMainActivity.disconnect();
                            finish();
                        }
                    });
                    AlertDialog a = builder.create();
                    a.show();
                }
            });
            return;
        }
    }

    /**
     * Handles various events fired by MainActivity
     * ACTION_GATT_CONNECTED: connected to a GATT server.
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
     * ACTION_DATA_NOTIFY: characteristic notification received.
     * ACTION_DATA_READ: characteristic have been read.
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            byte [] value;
            String uuidStr;
            switch(action){
                case MainActivity.ACTION_GATT_CONNECTED:
                    mIsConnected = true;
                    updateConnectionState(R.string.connected);
                    break;
                case MainActivity.ACTION_GATT_DISCONNECTED:
                    mIsConnected = false;
                    updateConnectionState(R.string.disconnected);
                    break;
                case MainActivity.ACTION_GATT_SERVICES_DISCOVERED:
                    initializeGattServiceUIElements(context, mMainActivity.getSupportedGattServices());
                    break;
                case MainActivity.ACTION_DATA_NOTIFY:
                    value = intent.getByteArrayExtra(MainActivity.EXTRA_DATA);
                    uuidStr = intent.getStringExtra(MainActivity.EXTRA_UUID);
                    if (uuidStr.equals(mCharIdentify.getUuid().toString())) {
                        // Image verification failed when this notification is received. Create message.
                        if (Util.ERROR) Log.e(TAG, "Image verification failed");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(FwUpdateActivity.this);
                        alertDialog.setTitle("Error");
                        alertDialog.setMessage("Image verification failed, cannot do OAD with this image.");
                        alertDialog.setNegativeButton("OK", null);
                        (alertDialog.create()).show();

                        // Disable program button
                        if (isProgramming()) {
                            mOadProcess.stopProgramming();
                        }

                    }
                    else if (uuidStr.equals(mCharBlock.getUuid().toString())) {
                        // Block request received
                        String block = String.format("%02x%02x",value[1],value[0]);
                        if (Util.DEBUG) Log.d(TAG, "Received block req: " + block);
                        if(mSafeMode) {
                            mOadProcess.programBlock();
                        }
                    }
                    else if(uuidStr.equals(mCharImageStatus.getUuid().toString())){
                        if((value[0] != 0) && !mOadFailedAlertShown){
                            // Oad error status received. Create dialog
                            String msg = "";
                            switch(value[0]){
                                case 1:
                                    msg = "The downloaded image’s CRC doesn’t match the one expected from the metadata.";
                                    break;
                                case 2:
                                    msg = "The external flash cannot be opened.";
                                    break;
                                case 3:
                                    msg = "A buffer overflow has occurred. Please try OAD in safe mode or with a longer " +
                                            "block delay.";
                                    break;
                                default:
                                    break;
                            }
                            mOadFailedAlertShown = true;
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(FwUpdateActivity.this);
                            alertDialog.setTitle("Error");
                            alertDialog.setMessage(String.format("OAD programming failed with status %02x: %s",value[0], msg));
                            alertDialog.setNegativeButton("OK", null);
                            (alertDialog.create()).show();
                            // Stop ongoing programming
                            if (isProgramming()) {
                                mOadProcess.stopProgramming();
                            }
                        }
                    }
                    break;
                case MainActivity.ACTION_DATA_READ:
                    value = intent.getByteArrayExtra(MainActivity.EXTRA_DATA);
                    uuidStr = intent.getStringExtra(MainActivity.EXTRA_UUID);
                    if (uuidStr.toString().compareTo(TIOADProfile.TEST_DATA_CHAR) == 0) {
                        // Test result data received, check data
                        if (value.length > 0) {
                            if (Util.DEBUG) Log.d(TAG, "Read from " + uuidStr + " data =" + value[0]);
                            updateFlashSelfTestState(value);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

}
