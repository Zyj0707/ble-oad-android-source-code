package com.example.ti.oadexample;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.DialogInterface;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Class containing the OAD handling
 */
public class OadProcess
{

    private FwUpdateActivity mFwUpdateActivity = null;
    private MainActivity mMainActivity = null;

    // Tag used for logging
    private static final String TAG = "OadProcess";

    // Bluetooth
    private BluetoothGattCharacteristic mCharIdentify = null;
    private BluetoothGattCharacteristic mCharBlock = null;

    // Programming
    private static final short OAD_CONN_INTERVAL = 12; // units of 1.25ms = 15ms
    private static final short OAD_SUPERVISION_TIMEOUT = 50; // units of 10 ms =  500 milliseconds
    private static final int OAD_BLOCK_SIZE = 16;
    private static final int OAD_BUFFER_SIZE = 2 + OAD_BLOCK_SIZE;
    private static final int HAL_FLASH_WORD_SIZE = 4;
    private static final long TIMER_INTERVAL = 1000;
    public static final int FILE_BUFFER_SIZE = 0x40000;
    private byte[] mFileBuffer = new byte[FILE_BUFFER_SIZE];
    private ImageHeader mFileImgHdr;
    private final byte[] mOadBuffer = new byte[OAD_BUFFER_SIZE];
    private ProgramInfo mProgramInfo = new ProgramInfo();
    private Timer mTimer = null;
    private TimerTask mTimerTask = null;
    private Handler mFastModeHandler;

    // Housekeeping
    private boolean mProgramming = false;
    boolean mImageHasHeader = false;


    /**
     * Constructor
     */
    public static OadProcess newInstance( MainActivity mainActivity, FwUpdateActivity fwUpdateActivity) {
        OadProcess oad = new OadProcess();
        oad.mCharBlock = fwUpdateActivity.mCharBlock;
        oad.mCharIdentify = fwUpdateActivity.mCharIdentify;
        oad.mFwUpdateActivity = fwUpdateActivity;
        oad.mMainActivity = mainActivity;
        return oad;
    }

    /**
     *  Inner class for fw image header info
     */
    private class ImageHeader {
        short crc0;
        short crc1;
        short version;
        int length;
        byte[] uid = new byte[4];
        short address;
        byte imgType;
        byte state;

        /**
         * Constructor
         * @param buffer buffer with image to program
         * @param fileLen length of image to program
         */
        ImageHeader(byte[] buffer, int fileLen) {

            // Check if image header exists in file
            if(fileLen > 15){
                int i = (mFileBuffer[11] << 24) | (mFileBuffer[10]<< 16) |
                        (mFileBuffer[9] << 8) | mFileBuffer[8];
                if (i== 0x45454545){
                    // Header exist, read it
                    mImageHasHeader = true;
                    this.length = Util.buildUint16(mFileBuffer[7], mFileBuffer[6]);
                    this.version = Util.buildUint16(mFileBuffer[5], mFileBuffer[4]);
                    this.uid[0] = this.uid[1] = this.uid[2] = this.uid[3] = 'E';
                    this.address = Util.buildUint16(mFileBuffer[13], mFileBuffer[12]);
                    this.imgType = mFileBuffer[14];
                    this.state = mFileBuffer[15];
                    this.crc0 = Util.buildUint16(mFileBuffer[1], mFileBuffer[0]);
                    crc1 = Util.buildUint16(mFileBuffer[3], mFileBuffer[2]);
                    if (Util.DEBUG) {
                        Log.d(TAG, "Read Header");
                        Log.d(TAG, "ImgHdr.length = " + this.length);
                        Log.d(TAG, "ImgHdr.version = " + this.version);
                        Log.d(TAG, String.format("ImgHdr.uid = %02x%02x%02x%02x", this.uid[0], this.uid[1], this.uid[2], this.uid[3]));
                        Log.d(TAG, "ImgHdr.address = " + this.address);
                        Log.d(TAG, "ImgHdr.imgType = " + this.imgType);
                        Log.d(TAG, String.format("ImgHdr.crc0 = %04x", this.crc0));
                    }

                    return;
                }
            }

            // Header not found in file, create one
            this.length = (fileLen / 4);
            this.version = 0;
            this.uid[0] = this.uid[1] = this.uid[2] = this.uid[3] = 'E';
            this.address = 0;
            this.imgType = 1; //EFL_OAD_IMG_TYPE_APP
            this.crc0 = calcImageCRC((int)0,buffer);
            crc1 = (short)0xFFFF;
            this.state = (byte)0xFF;
            if (Util.DEBUG) {
                Log.d(TAG, "Generated Header");
                Log.d(TAG, "ImgHdr.length = " + this.length);
                Log.d(TAG, "ImgHdr.version = " + this.version);
                Log.d(TAG, String.format("ImgHdr.uid = %02x%02x%02x%02x", this.uid[0], this.uid[1], this.uid[2], this.uid[3]));
                Log.d(TAG, "ImgHdr.address = " + this.address);
                Log.d(TAG, "ImgHdr.imgType = " + this.imgType);
                Log.d(TAG, String.format("ImgHdr.crc0 = %04x", this.crc0));
            }
        }

        /**
         * Function returning a byte array with image header
         */
        byte[] getRequest() {
            byte[] tmp = new byte[16];
            tmp[0] = Util.loUint16(this.crc0);
            tmp[1] = Util.hiUint16(this.crc0);
            tmp[2] = Util.loUint16(this.crc1);
            tmp[3] = Util.hiUint16(this.crc1);
            tmp[4] = Util.loUint16(this.version);
            tmp[5] = Util.hiUint16(this.version);
            tmp[6] = Util.loUint16((short)this.length);
            tmp[7] = Util.hiUint16((short)this.length);
            tmp[8] = tmp[9] = tmp[10] = tmp[11] = this.uid[0];
            tmp[12] = Util.loUint16(this.address);
            tmp[13] = Util.hiUint16(this.address);
            tmp[14] = imgType;
            tmp[15] = (byte) 0xFF;
            return tmp;
        }

        /**
         * Calculate the CRC of image to program
         */
        short calcImageCRC(int page, byte[] buf) {
            short crc = 0;
            long addr = page * 0x1000;

            byte pageBeg = (byte)page;
            byte pageEnd = (byte)(this.length / (0x1000 / 4));
            int osetEnd = ((this.length - (pageEnd * (0x1000 / 4))) * 4);

            pageEnd += pageBeg;

            while (true) {
                int oset;

                for (oset = 0; oset < 0x1000; oset++) {
                    if ((page == pageBeg) && (oset == 0x00)) {
                        // Skip the CRC and shadow.
                        // Note: this increments by 3 because oset is incremented by 1 in each pass
                        // through the loop
                        oset += 3;
                    }
                    else if ((page == pageEnd) && (oset == osetEnd)) {
                        crc = this.crc16(crc,(byte)0x00);
                        crc = this.crc16(crc,(byte)0x00);

                        return crc;
                    }
                    else {
                        crc = this.crc16(crc,buf[(int)(addr + oset)]);
                    }
                }
                page += 1;
                addr = page * 0x1000;
            }
        }

        short crc16(short crc, byte val) {
            final int poly = 0x1021;
            byte cnt;
            for (cnt = 0; cnt < 8; cnt++, val <<= 1) {
                byte msb;
                if ((crc & 0x8000) == 0x8000) {
                    msb = 1;
                }
                else msb = 0;

                crc <<= 1;
                if ((val & 0x80) == 0x80) {
                    crc |= 0x0001;
                }
                if (msb == 1) {
                    crc ^= poly;
                }
            }

            return crc;
        }
    }

    /**
     * Inner class for the programming timer
     */
    private class ProgramTimerTask extends TimerTask {
        @Override
        public void run() {
            mProgramInfo.iTimeElapsed += TIMER_INTERVAL;
        }
    }

    /**
     * Inner class keeping programming status
     */
    private class ProgramInfo {
        int iBytes = 0; // Number of bytes programmed
        short iBlocks = 0; // Number of blocks programmed
        short nBlocks = 0; // Total number of blocks
        int iTimeElapsed = 0; // Time elapsed in milliseconds

        void reset() {
            iBytes = 0;
            iBlocks = 0;
            iTimeElapsed = 0;
            nBlocks = (short) (mFileImgHdr.length / (OAD_BLOCK_SIZE / HAL_FLASH_WORD_SIZE));
        }
    }

    /**
     * Display the programming progress
     */
    private void displayProgress() {
        String txt;
        int byteRate;
        int sec = mProgramInfo.iTimeElapsed / 1000;
        if (sec > 0) {
            byteRate = mProgramInfo.iBytes / sec;
        } else {
            return;
        }
        float timeEstimate = ((float)(mFileImgHdr.length *4) / (float)mProgramInfo.iBytes) * sec;

        txt = String.format("Time: %d / %d sec", sec, (int)timeEstimate);
        txt += String.format("    Bytes: %d (%d/sec)", mProgramInfo.iBytes, byteRate);
        mFwUpdateActivity.displayProgressText(txt);
    }

    /**
     *  Check if programming is in action
     */
    public boolean isProgramming()
    {
        return mProgramming;
    }

    /**
     * Called when the user has chosen a file
     */
    public int readFile(String filepath) {
        int readLen = 0;

        // Load binary file
        try {
            // Read the file raw into a buffer
            InputStream stream;
            File f = new File(filepath);
            stream = new FileInputStream(f);
            readLen = stream.read(mFileBuffer, 0, mFileBuffer.length);
            stream.close();
        } catch (IOException e) {
            // Handle exceptions here
            mFwUpdateActivity.log("File open failed: " + filepath + "\n", false);
            return -1;
        }

        // Pad image with 0xFF to align with 16 bytes
        if((readLen % 16) != 0){
            Log.d(TAG, "length = " + mFileBuffer.length);
            while((readLen % 16) != 0){
                mFileBuffer[readLen] = (byte)0xFF;
                readLen++;
            }
        }

        // Create image header
        mFileImgHdr = new ImageHeader(mFileBuffer,readLen);
        if (mImageHasHeader && (mFileImgHdr.state == (byte)0xFE))
        {
            // Image header is written in the file, do not include it in the programming
            System.arraycopy(mFileBuffer, 16,mFileBuffer, 0, mFileBuffer.length - 16);
            readLen -= 16;
        }

        return readLen;
    }

    /**
    * Program one block of bytes.
    * In safe mode, this function is called when a notification with the current image info
    * has been received. In unsafe mode, it is called repeatedly with a delay.
    */
    public void programBlock() {
        if (!mProgramming)
            return;

        if (mProgramInfo.iBlocks < mProgramInfo.nBlocks)
        {
            mProgramming = true;

            // Prepare block
            mOadBuffer[0] = Util.loUint16(mProgramInfo.iBlocks);
            mOadBuffer[1] = Util.hiUint16(mProgramInfo.iBlocks);
            System.arraycopy(mFileBuffer, mProgramInfo.iBytes, mOadBuffer, 2, OAD_BLOCK_SIZE);

            // Send block
            mCharBlock.setValue(mOadBuffer);
            mMainActivity.writeCharacteristicNoResponse(mCharBlock);
            String block = String.format("%02x%02x",mOadBuffer[1],mOadBuffer[0]);
            if (Util.DEBUG) Log.d(TAG,"Sent block :" + block /*mProgramInfo.iBlocks*/);

            // Update statistics
            mProgramInfo.iBlocks++;
            mProgramInfo.iBytes += OAD_BLOCK_SIZE;
            mFwUpdateActivity.updateProgressBar((mProgramInfo.iBlocks * 100) / mProgramInfo.nBlocks);
            if (mProgramInfo.iBlocks == mProgramInfo.nBlocks) {

                // Programming has finished
                AlertDialog.Builder b = new AlertDialog.Builder(mFwUpdateActivity);
                b.setMessage(R.string.oad_dialog_programming_finished);
                b.setTitle("Programming finished");
                b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mFwUpdateActivity.finish();
                    }
                });

                AlertDialog d = b.create();
                d.show();
                mProgramming = false;
                mFwUpdateActivity.log(("Programming finished at block " + (mProgramInfo.iBlocks + 1) + "\n"), true);
            }

        }
        else
        {
            mProgramming = false;
        }

        if ((mProgramInfo.iBlocks % 100) == 0) {
            // Display statistics each 100th block
            mFwUpdateActivity.runOnUiThread(new Runnable() {
                public void run() {
                    displayProgress();
                }
            });
        }

        if (!mProgramming)
        {
            mFwUpdateActivity.runOnUiThread(new Runnable() {
                public void run() {
                    displayProgress();
                    stopProgramming();
                }
            });
        }
    }

    /**
     * Start programming image
     */
    public void startProgramming() {

        // Enable notifications on characteristics
        mMainActivity.setCharacteristicNotification(mCharIdentify,true);
        mMainActivity.setCharacteristicNotification(mCharBlock,true);
        BluetoothGattCharacteristic imageStatusChar = mFwUpdateActivity.mCharImageStatus;
        mMainActivity.setCharacteristicNotification(imageStatusChar,true);

        // Send image header
        mCharIdentify.setValue(mFileImgHdr.getRequest());
        mMainActivity.writeCharacteristic(mCharIdentify);

        // Update GUI
        mFwUpdateActivity.log("Programming started\n", true);
        mProgramming = true;
        mFwUpdateActivity.updateGui(mProgramming);

        // Initialize statistics
        mProgramInfo.reset();
        mTimer = new Timer();
        mTimerTask = new ProgramTimerTask();
        mTimer.scheduleAtFixedRate(mTimerTask, 0, TIMER_INTERVAL);

        if(!mFwUpdateActivity.mSafeMode){
            // Fast mode. Start runnable that program blocks with a delay
            mFastModeHandler = new Handler();
            mFastModeHandler.postDelayed(r, 150);
        }
    }

    /**
     * Runnable used for fast programming mode. Blocks of bytes
     * are programmed continuously with a delay
     */
    private Runnable r = new Runnable() {

        @Override
        public void run() {
            if(mProgramming){
                // Program block and delay
                programBlock();
                mFastModeHandler.postDelayed(this, mFwUpdateActivity.mBlockDelay);
            }
            else{
                // Stop runnable
                mFastModeHandler.removeCallbacks(this);
            }
        }
    };

    /**
     * Stop programming of image
     */
    public void stopProgramming() {
        mTimer.cancel();
        mTimer.purge();
        mTimerTask.cancel();
        mTimerTask = null;

        mProgramming = false;
        mFwUpdateActivity.displayProgressText("");
        mFwUpdateActivity.updateProgressBar(0);
        mFwUpdateActivity.updateGui(mProgramming);

        if (mProgramInfo.iBlocks == mProgramInfo.nBlocks) {
            mFwUpdateActivity.log("Programming complete!\n", false);
        } else {
            mFwUpdateActivity.log("Programming cancelled\n", true);
        }

        // Disable notification on characteristics
        mMainActivity.setCharacteristicNotification(mCharBlock, false);
    }

    /**
     * Function trying to set the BLE connection parameters
     */
    public void setConnectionParameters() {
        // Make sure connection interval is long enough for OAD
        byte[] value = {Util.loUint16(OAD_CONN_INTERVAL), Util.hiUint16(OAD_CONN_INTERVAL), Util.loUint16(OAD_CONN_INTERVAL),
                Util.hiUint16(OAD_CONN_INTERVAL), 0, 0, Util.loUint16(OAD_SUPERVISION_TIMEOUT), Util.hiUint16(OAD_SUPERVISION_TIMEOUT) };

        BluetoothGattCharacteristic charConnReq = mFwUpdateActivity.getCharConnReq();
        charConnReq.setValue(value);
        mMainActivity.writeCharacteristic(charConnReq);
    }

}
