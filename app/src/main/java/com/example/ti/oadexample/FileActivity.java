package com.example.ti.oadexample;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;


public class FileActivity extends AppCompatActivity {

    public final static String EXTRA_FILENAME = "com.example.ti.oadexample.FILENAME";
    private static final String TAG = "FileActivity";

    // GUI
    private FileAdapter mFileAdapter;
    private ListView mLvFileList;
    private EditText mEtDirName;
    private Button mBtnConfirm;

    // Housekeeping
    private String mSelectedFile;
    private List<String> mFileList;
    private File mDir;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);

        // Set default directory
        mDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        // Initialize GUI elements
        mEtDirName = (EditText) findViewById(R.id.et_directory);
        mBtnConfirm = (Button) findViewById(R.id.btn_confirm);
        mLvFileList = (ListView) findViewById(R.id.lv_file);
        mLvFileList.setOnItemClickListener(mFileClickListener);

        // Display path in GUI
        mEtDirName.setText(mDir.getAbsolutePath());
        mEtDirName.setSelection(mEtDirName.getText().length());

        // Display files found in path
        populateFileList();

    }
    @Override
    public void onDestroy() {
        mFileList = null;
        mFileAdapter = null;
        super.onDestroy();
    }


    /**
     * Function called when the user reloads the file directory
     */
    public void onDirChanged(View view) {
        // Save the new directory and populate the list view
        mDir = new File(mEtDirName.getText().toString());
        populateFileList();
    }

    /**
     * Listener for file click
     */
    private OnItemClickListener mFileClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {

            // A file item has been selected
            mFileAdapter.setSelectedPosition(pos);
        }
    };

    /**
     * Callback for confirm button
     */
    public void onConfirm(View v) {
        Intent i = new Intent();

        // Save selected file (if any) and finish activity
        if (mFileList.size() > 0) {
            i.putExtra(EXTRA_FILENAME, mDir.getAbsolutePath() + File.separator + mSelectedFile);
            setResult(RESULT_OK, i);
        } else {
            setResult(RESULT_CANCELED, i);
        }
        finish();
    }

    /**
    * FileAdapter class: Handle the file list
    */
    class FileAdapter extends BaseAdapter {
        Context mContext;
        List<String> mFiles;
        LayoutInflater mInflater;
        int mSelectedPos;

        public FileAdapter(Context context, List<String> files) {
            mInflater = LayoutInflater.from(context);
            mContext = context;
            mFiles = files;
            mSelectedPos = 0;
        }

        @Override
        public int getCount() {
            return mFiles.size();
        }

        @Override
        public Object getItem(int pos) {
            return mFiles.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(int pos, View view, ViewGroup parent) {
            ViewGroup vg;

            if (view != null) {
                vg = (ViewGroup) view;
            } else {
                vg = (ViewGroup) mInflater.inflate(R.layout.element_file, null);
            }

            // Grab file object
            String file = mFiles.get(pos);

            // Show file name
            TextView tvName = (TextView) vg.findViewById(R.id.name);
            tvName.setText(file);

            // Highlight selected object
            if (pos == mSelectedPos) {
                TextViewCompat.setTextAppearance(tvName, R.style.nameStyleSelected);
            } else {
                TextViewCompat.setTextAppearance(tvName, R.style.nameStyle);
            }

            return vg;
        }

        /**
         * Function called when a file has been selected.
         */
        public void setSelectedPosition(int pos) {
            mSelectedFile = mFileList.get(pos);
            mSelectedPos = pos;
            notifyDataSetChanged();
        }

    }

    /**
     * List all .bin files located at the selected directory
     */
    public void populateFileList()
    {
        // Create a list of files
        mFileList = new ArrayList<>();
        mFileAdapter = new FileAdapter(this, mFileList);
        mLvFileList.setAdapter(mFileAdapter);

        if (mDir.exists() && mDir.canRead()) {
            if(Util.DEBUG) Log.d(TAG, mDir.getPath());

            // Create filter on .bin files
            FilenameFilter textFilter = new FilenameFilter()
            {
                public boolean accept(File dir, String name)
                {
                    String lowercaseName = name.toLowerCase();
                    if (lowercaseName.endsWith(".bin"))
                    {
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }
            };

            // Create array of all .bin files
            File[] files = mDir.listFiles(textFilter);
            if (files == null) {
                // Show error dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Error")
                        .setMessage("Could not access internal file storage.")
                        .setCancelable(false)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        finish();
                                    }
                                })
                        .show();
                return;
            }

            for (File file : files)
            {
                if (!file.isDirectory())
                {
                    mFileList.add(file.getName());
                }
            }

            if (mFileList.size() == 0)
            {
                Toast.makeText(this, "No OAD images available", Toast.LENGTH_LONG).show();
            }
        }
        else
        {
            Toast.makeText(this, Environment.DIRECTORY_DOWNLOADS + " does not exist or is not readable", Toast.LENGTH_LONG).show();
        }

        // Select the first item as default
        if (mFileList.size() > 0)
        {
            mFileAdapter.setSelectedPosition(0);
            mBtnConfirm.setText("Confirm");
        }
        else
        {
            mBtnConfirm.setText("Cancel");
        }

    }

}
