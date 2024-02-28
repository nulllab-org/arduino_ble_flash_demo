package com.nulllab.ble.coding.central;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import com.nulllab.ble.coding.central.util.IntelHexUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    final String[] mHexFileNames = {"fast_blink.hex", "slow_blink.hex"};
    private final List<String> mBleAddressList = new LinkedList<>();
    private final Set<String> mBleAddressSet = new HashSet<>();
    ArrayAdapter<String> mDialogAdapter = null;
    ArrayAdapter<String> mBleAddressSpinnerAdapter = null;
    Spinner mBleAddressSpinner = null;
    SwitchCompat mConnectSwitch = null;
    Spinner mHexSpinner = null;

    ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "onScanResult: " + result);
            String address = result.getDevice().getAddress();
            if (!mBleAddressSet.contains(address)) {
                mBleAddressSet.add(address);
                mBleAddressList.add(address);
                mDialogAdapter.notifyDataSetChanged();
                mBleAddressSpinnerAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG, "onBatchScanResults: " + results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: " + errorCode);
        }
    };
    private LogView mLogView;
    private TextView mStateTextView;
    private final BleCodingPeripheral mBleCodingPeripheral = new BleCodingPeripheral(this, new BleCodingPeripheral.Listener() {
        @Override
        public void onStateChange(BleCodingPeripheral.State previous_state, BleCodingPeripheral.State state) {
            Log.d(TAG, "onStateChange: " + state);
            mStateTextView.setText(state.toString().toLowerCase().replace('_', ' '));

            switch (state) {
                case CONNECTED:
                    if (previous_state == BleCodingPeripheral.State.DISCONNECTED) {
                        Toast.makeText(MainActivity.this, "device is connected", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case DISCONNECTED:
                    Toast.makeText(MainActivity.this, "device is disconnected", Toast.LENGTH_SHORT).show();
                    mConnectSwitch.setChecked(false);
                    break;
            }
        }

        @Override
        public void onReceivedFromSerial(byte[] data) {
            mLogView.append(data);
        }

        @Override
        public void onFlashProcess(int totalLength, int flashedLength) {
            Log.i(TAG, "onFlashProcess: " + flashedLength + "/" + totalLength);
            mLogView.write("(" + flashedLength + "/" + totalLength + ")\n");
        }
    });

    @Override
    @SuppressLint("MissingPermission")
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDialogAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice, mBleAddressList);
        mBleAddressSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mBleAddressList);
        mBleAddressSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mConnectSwitch = findViewById(R.id.connect_switch);
        mLogView = findViewById(R.id.log_text_view);
        mStateTextView = findViewById(R.id.state_text_view);
        mBleAddressSpinner = findViewById(R.id.ble_address_spinner);

        mConnectSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "onCheckedChanged: " + isChecked);
            if (!enableBluetooth()) {
                mConnectSwitch.setChecked(false);
                return;
            }
            if (isChecked) {
                if (mBleAddressSpinner.getSelectedItem() == null) {
                    mConnectSwitch.setChecked(false);
                    Toast.makeText(MainActivity.this, "please select a ble address", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String address = mBleAddressSpinner.getSelectedItem().toString();
                BleCodingPeripheral.ResultCode ret = mBleCodingPeripheral.connect(address);
                if (BleCodingPeripheral.ResultCode.OK != ret) {
                    Log.e(TAG, "failed to connect " + address + ": " + ret);
                    Toast.makeText(MainActivity.this, "failed to connect " + address + ": " + ret, Toast.LENGTH_SHORT).show();
                    mConnectSwitch.setChecked(false);
                }
            } else {
                mBleCodingPeripheral.disconnect();
            }
        });

        mBleAddressSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "onItemSelected: " + position);
                mBleAddressSpinner.setSelection(position);
                mConnectSwitch.setChecked(false);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "onNothingSelected: ");
                mConnectSwitch.setChecked(false);
            }
        });
        mBleAddressSpinner.setAdapter(mBleAddressSpinnerAdapter);

        findViewById(R.id.scan_button).setOnClickListener(v -> {
            if (!checkAndRequestScanPermissions()) {
                return;
            }
            if (!enableBluetooth()) {
                return;
            }
            mBleCodingPeripheral.disconnect();
            mBleAddressSpinnerAdapter.clear();
            mBleAddressSpinnerAdapter.notifyDataSetChanged();
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setSingleChoiceItems(mDialogAdapter, 0, (dialog, which) -> ((Spinner) findViewById(R.id.ble_address_spinner)).setSelection(which))
                    .setOnCancelListener(dialog -> BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner().stopScan(mScanCallback))
                    .setTitle("scanning...")
                    .setPositiveButton("OK", (dialog, which) -> BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner().stopScan(mScanCallback));
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            startScan();
        });

        mHexSpinner = findViewById(R.id.hex);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList(mHexFileNames));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mHexSpinner.setAdapter(adapter);
        mHexSpinner.setSelection(0);

        findViewById(R.id.send).setOnClickListener(v -> {
            if (!checkAndRequestScanPermissions()) {
                return;
            }
            if (!enableBluetooth()) {
                return;
            }

            Log.d(TAG, "send " + mHexSpinner.getSelectedItem());
            final String hex = mHexSpinner.getSelectedItem().toString();
            new Thread(() -> {
                byte[] bin = null;
                try {
//                        bin = IntelHexUtils.hexToBin(new BufferedReader(new InputStreamReader(getAssets().open("hex/uno_print_hello_world.hex"))));
//                        bin = IntelHexUtils.hexToBin(new BufferedReader(new InputStreamReader(getAssets().open("hex/uno_print_test.hex"))));
                    bin = IntelHexUtils.hexToBin(new BufferedReader(new InputStreamReader(getAssets().open("hex/" + hex))));
//                        InputStream inputStream = getAssets().open("bin/print_test.bin");
//                        bin = new byte[inputStream.available()];
//                        inputStream.read(bin);
//                        inputStream.close();
                    Log.d(TAG, "arex bin length: " + bin.length);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                BleCodingPeripheral.ResultCode ret = mBleCodingPeripheral.flashBin(bin);
//                    if (BleCodingPeripheral.ResultCode.OK != ret) {
//                        Log.e(TAG, "failed to send file, " + ret);
//                        Toast.makeText(this, "failed to send file: " + ret, Toast.LENGTH_SHORT).show();
//                    }
            }).start();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart: ");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        mConnectSwitch.setChecked(false);
        View rootView = getWindow().getDecorView().getRootView();
        rootView.clearFocus();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    private boolean checkAndRequestScanPermissions() {
        String[] permissions = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        } else {
            permissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        }

        List<String> request_permissions = new ArrayList<>();

        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "requesting " + permission, Toast.LENGTH_SHORT).show();
                request_permissions.add(permission);
            }
        }

        if (request_permissions.isEmpty()) {
            return true;
        }

        requestPermissions(request_permissions.toArray(new String[0]), 0);

        return false;
    }

    private boolean enableBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "checkBluetooth: device doesn't support Bluetooth");
            Toast.makeText(this, "device doesn't support Bluetooth", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 0);
            Toast.makeText(this, "requesting android.permission.BLUETOOTH_CONNECT", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "checkBluetooth: bluetooth is disabled");
            Toast.makeText(this, "enable Bluetooth and try again.", Toast.LENGTH_SHORT).show();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            return false;
        }

        return true;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    private void startScan() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "connect: device doesn't support Bluetooth");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "connect: bluetooth is disabled");
            return;
        }
        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        ScanSettings scanSettings = new ScanSettings.Builder().setMatchMode(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build();

        ScanFilter scanFilter = new ScanFilter.Builder().setDeviceName("ble-uno4.2").build();
        mBleAddressSet.clear();
        mBleAddressList.clear();
        mDialogAdapter.notifyDataSetChanged();
        bluetoothLeScanner.startScan(Collections.singletonList(scanFilter), scanSettings, mScanCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < permissions.length; i++) {
            Toast.makeText(this, permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"), Toast.LENGTH_SHORT).show();
        }
    }
}