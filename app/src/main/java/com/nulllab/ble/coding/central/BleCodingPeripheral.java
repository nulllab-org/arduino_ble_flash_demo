package com.nulllab.ble.coding.central;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.nulllab.ble.coding.central.util.MainThreadUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class BleCodingPeripheral {
    private static final String TAG = "BleCodingPeripheral";
    private static final int MTU_MAX = 517;
    private static final int MTU_MIN = 23;
    private static final UUID SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_AT_UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");
    private static final UUID SERIAL_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    private final Context mContext;
    private final Listener mListener;
    //    private final ByteBuffer mReceivedData = new ByteBuffer();
    private final List<Byte> mReceivedData = new LinkedList<>();
    private boolean mStoreReceivedData = false;
    private BluetoothGatt mBluetoothGatt;
    private int mMtu = MTU_MIN;
    private State mState = State.DISCONNECTED;
    private CharacterWritingState mCharacterWritingState = CharacterWritingState.IDLE;
    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange, newState: " + newState);
            synchronized (BleCodingPeripheral.this) {
                if (mState == State.CONNECTING && newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "onConnectionStateChange: connected and request mtu");
                    gatt.requestMtu(MTU_MAX);
                    changeState(State.CONFIGURING_MTU);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && mState != State.DISCONNECTED) {
                    Log.d(TAG, "onConnectionStateChange: disconnected");
                    reset();
                    changeState(State.DISCONNECTED);
                }
            }
        }

        @Override
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered: ");
            synchronized (BleCodingPeripheral.this) {
                if (mState == State.DISCOVERING_SERVICES) {
                    for (BluetoothGattService service : mBluetoothGatt.getServices()) {
                        Log.d(TAG, "onServicesDiscovered, service:" + service.getUuid());
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            Log.d(TAG, "onServicesDiscovered, characteristic:" + characteristic.getUuid());
                        }
                    }
                    final BluetoothGattCharacteristic characteristic = mBluetoothGatt.getService(SERVICE_UUID).getCharacteristic(SERIAL_UUID);
                    mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                    for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(descriptor);
                    }
                    changeState(State.WRITING_DESCRIPTOR);
                }
            }
        }

        @Override
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite: " + status);
            synchronized (BleCodingPeripheral.this) {
                if (mCharacterWritingState == CharacterWritingState.WRITING) {
                    mCharacterWritingState = CharacterWritingState.IDLE;
                    BleCodingPeripheral.this.notifyAll();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return;
            }

            synchronized (BleCodingPeripheral.this) {
                if (mStoreReceivedData) {
                    for (byte b : characteristic.getValue()) {
                        mReceivedData.add(b);
                    }
                    BleCodingPeripheral.this.notifyAll();
                } else if (mListener != null) {
                    mListener.onReceivedFromSerial(characteristic.getValue());
                }
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);

            synchronized (BleCodingPeripheral.this) {
                if (mStoreReceivedData) {
                    for (byte b : value) {
                        mReceivedData.add(b);
                    }
                    BleCodingPeripheral.this.notifyAll();
                } else if (mListener != null) {
                    mListener.onReceivedFromSerial(characteristic.getValue());
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite: ");
            synchronized (BleCodingPeripheral.this) {
                if (mState == State.WRITING_DESCRIPTOR) {
                    changeState(State.CONNECTED);
                }
            }
        }

        @Override
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.d(TAG, "onMtuChanged: " + mtu);
            synchronized (BleCodingPeripheral.this) {
                if (mState == State.CONFIGURING_MTU) {
                    mMtu = mtu;
                    mBluetoothGatt.discoverServices();
                    changeState(State.DISCOVERING_SERVICES);
                }
            }
        }

        @Override
        public void onServiceChanged(@NonNull BluetoothGatt gatt) {
            super.onServiceChanged(gatt);
            Log.d(TAG, "onServiceChanged: ");
        }
    };
    private TransmissionState mTransmissionState = TransmissionState.IDLE;

    public BleCodingPeripheral(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public synchronized ResultCode connect(final String bleAddress) {
        if (mState != State.DISCONNECTED) {
            Log.w(TAG, "connect: current state is not disconnected");
            return ResultCode.INVALID_STATE;
        }

        reset();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "connect: device doesn't support Bluetooth");
            return ResultCode.BLUETOOTH_UNSUPPORTED;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "connect: bluetooth is disabled");
            return ResultCode.BLUETOOTH_DISABLED;
        }

        changeState(State.CONNECTING);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(bleAddress.toUpperCase());
        mBluetoothGatt = device.connectGatt(mContext, false, mBluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
        return ResultCode.OK;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public synchronized ResultCode disconnect() {
        if (mState == State.DISCONNECTED) {
            return ResultCode.OK;
        }

        reset();
        changeState(State.DISCONNECTED);
        return ResultCode.OK;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public synchronized ResultCode flashBin(byte[] data) {
        Log.i(TAG, "flashBin: ");
        if (mListener != null) {
            mListener.onFlashProcess(data.length, 0);
        }
        hardReset();
        try {
            Thread.sleep(80);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!writeCharacteristicAndWaitResponse(SERIAL_UUID, new byte[]{ConstantsStk500v1.STK_GET_SYNC, ConstantsStk500v1.CRC_EOP}, new byte[]{ConstantsStk500v1.STK_INSYNC, ConstantsStk500v1.STK_OK})) {
            Log.e(TAG, "failed to write STK_GET_SYNC");
            return ResultCode.UNKNOWN_ERROR;
        }
        if (!writeCharacteristicAndWaitResponse(SERIAL_UUID, new byte[]{ConstantsStk500v1.STK_ENTER_PROGMODE, ConstantsStk500v1.CRC_EOP}, new byte[]{ConstantsStk500v1.STK_INSYNC, ConstantsStk500v1.STK_OK})) {
            Log.e(TAG, "failed to write STK_ENTER_PROGMODE");
            return ResultCode.UNKNOWN_ERROR;
        }

        byte[] command = new byte[6];
        command[0] = ConstantsStk500v1.STK_UNIVERSAL;
        command[1] = (byte) 172;
        command[2] = (byte) 128;
        command[3] = (byte) 0;
        command[4] = (byte) 0;
        command[5] = ConstantsStk500v1.CRC_EOP;

        if (!writeCharacteristicAndWaitResponse(SERIAL_UUID, command, new byte[]{ConstantsStk500v1.STK_INSYNC, 0, ConstantsStk500v1.STK_OK})) {
            Log.e(TAG, "failed to write STK_UNIVERSAL");
            return ResultCode.UNKNOWN_ERROR;
        }

        int position = 0;
        while (position < data.length) {
            int length = Integer.min(128, data.length - position);

            byte[] tempAddr = packTwoBytes(position / 2);
            byte[] loadAddr = new byte[4];

            loadAddr[0] = ConstantsStk500v1.STK_LOAD_ADDRESS;
            loadAddr[1] = tempAddr[1];
            loadAddr[2] = tempAddr[0];
            loadAddr[3] = ConstantsStk500v1.CRC_EOP;

            if (!writeCharacteristicAndWaitResponse(SERIAL_UUID, loadAddr, new byte[]{ConstantsStk500v1.STK_INSYNC, ConstantsStk500v1.STK_OK})) {
                Log.e(TAG, "failed to write loadAddr");
                return ResultCode.UNKNOWN_ERROR;
            }

//            int length = Integer.min((MTU_MIN - 3 - 5) & 0xFFFFFFFE, data.length - position);
//            int ble_packet_length = length + 5;
            Log.i(TAG, "sendFile: position:" + position + ", length: " + length);
            byte[] programPage = new byte[4];
            programPage[0] = ConstantsStk500v1.STK_PROG_PAGE;
            programPage[1] = (byte) ((length >> 8) & 0xFF);
            programPage[2] = (byte) (length & 0xFF);
            programPage[3] = 'F';
            writeCharacteristic(SERIAL_UUID, programPage);
            for (int i = 0; i < length; ) {
                int splitLength = Integer.min(mMtu - 3, length - i);
                Log.i(TAG, "splitLength: " + splitLength);
                writeCharacteristic(SERIAL_UUID, Arrays.copyOfRange(data, position + i, position + i + splitLength));
                i += splitLength;
            }
            if (!writeCharacteristicAndWaitResponse(SERIAL_UUID, new byte[]{ConstantsStk500v1.CRC_EOP}, new byte[]{ConstantsStk500v1.STK_INSYNC, ConstantsStk500v1.STK_OK})) {
                Log.e(TAG, "failed to write programPage");
                return ResultCode.UNKNOWN_ERROR;
            }

            position += length;
            if (mListener != null) {
                mListener.onFlashProcess(data.length, position);
            }
        }
        if (!writeCharacteristicAndWaitResponse(SERIAL_UUID, new byte[]{ConstantsStk500v1.STK_LEAVE_PROGMODE, ConstantsStk500v1.CRC_EOP}, new byte[]{ConstantsStk500v1.STK_INSYNC, ConstantsStk500v1.STK_OK})) {
            Log.e(TAG, "failed to write STK_LEAVE_PROGMODE");
        }
        return ResultCode.OK;
    }

    private byte[] packTwoBytes(int integer) {
        byte[] bytes = new byte[2];
        // store the 8 least significant bits
        bytes[1] = (byte) (integer & 0xFF);
        // store the next 8 bits
        bytes[0] = (byte) ((integer >> 8) & 0xFF);
        return bytes;
    }

    private synchronized boolean writeCharacteristicAndWaitResponse(UUID uuid, byte[] data, byte[] response) {
        if (mState != State.CONNECTED) {
            Log.e(TAG, "writeCharacteristicAndWaitResponse: invalid state: " + mState);
            return false;
        }

        try {
            mReceivedData.clear();
            mStoreReceivedData = true;

            if (!writeCharacteristic(uuid, data)) {
                Log.e(TAG, "writeCharacteristicAndWaitResponse: failed to writeCharacteristic");
                return false;
            }

            long startTime = System.currentTimeMillis();
            long elapsedTime = 0;
            long timeoutMs = 500;

            for (byte b : response) {
                while (mReceivedData.isEmpty() && elapsedTime < timeoutMs) {
                    wait(timeoutMs - elapsedTime);
                    elapsedTime = System.currentTimeMillis() - startTime;
                }

                if (mReceivedData.isEmpty()) {
                    throw new TimeoutException("read data timeouted");
                }

                byte read = mReceivedData.remove(0);
                Log.d(TAG, "writeCharacteristicAndWaitResponse: " + b + ", " + read);
                if (b != read) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            mStoreReceivedData = false;
            mReceivedData.clear();
        }

        return true;
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private synchronized boolean writeCharacteristic(UUID uuid, byte[] value) {
        if (mState != State.CONNECTED) {
            Log.e(TAG, "writeCharacteristic, invalid state: " + mState);
            return false;
        }

        if (mCharacterWritingState != CharacterWritingState.IDLE) {
            Log.e(TAG, "writeCharacteristic, invalid CharacterWritingState: " + mCharacterWritingState);
            return false;
        }

        BluetoothGattCharacteristic characteristic = mBluetoothGatt.getService(SERVICE_UUID).getCharacteristic(uuid);
        mCharacterWritingState = CharacterWritingState.WRITING;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mBluetoothGatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(value);
            mBluetoothGatt.writeCharacteristic(characteristic);
        }

        while (mCharacterWritingState != CharacterWritingState.IDLE) {
            try {
                wait(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        long startTime = System.currentTimeMillis();
        long timeoutMs = 1000;
        long elapsedTime = 0;

        while (mState == State.CONNECTED && mCharacterWritingState == CharacterWritingState.WRITING && elapsedTime < timeoutMs) {
            try {
                wait(timeoutMs - elapsedTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            elapsedTime = System.currentTimeMillis() - startTime;
        }

        if (mCharacterWritingState != CharacterWritingState.IDLE) {
            mCharacterWritingState = CharacterWritingState.IDLE;
            return false;
        } else {
            return true;
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private synchronized void reset() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
        }
        mBluetoothGatt = null;
        mMtu = MTU_MIN;
        mState = State.DISCONNECTED;
        mStoreReceivedData = false;
        mCharacterWritingState = CharacterWritingState.IDLE;
        notifyAll();
    }

    private synchronized void changeState(State state) {
        State previous_state = mState;
        mState = state;
        MainThreadUtils.run(() -> {
            if (mListener != null) {
                mListener.onStateChange(previous_state, state);
            }
        });
        notifyAll();
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private synchronized void softReset() {
        // Bytes needed to reset arduino using the ComputerSerial library
        Log.d(TAG, "softReset: ");
        byte[] write = new byte[]{(byte) 0xFF, 0x00, 0x01, (byte) 0xFF, 0x00, 0x00};
        writeCharacteristic(SERIAL_UUID, write);
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private synchronized void hardReset() {
        // Bytes needed to reset arduino using the ComputerSerial library
        Log.d(TAG, "hardReset: ");
        String cmd = "AT+TARGE_RESET\r\n";
        writeCharacteristic(BLE_AT_UUID, cmd.getBytes());
    }

    public enum State {
        DISCONNECTED, CONNECTING, CONFIGURING_MTU, DISCOVERING_SERVICES, WRITING_DESCRIPTOR, CONNECTED
    }

    public enum TransmissionState {
        IDLE,
        FLASHING,
    }

    public enum CharacterWritingState {
        IDLE,
        WRITING,
    }

    public enum ResultCode {
        OK, INVALID_STATE, INVALID_ARGUMENTS, BLUETOOTH_UNSUPPORTED, BLUETOOTH_DISABLED, UNKNOWN_ERROR,
    }

    public interface Listener {
        void onStateChange(State previous_state, State new_state);

        void onReceivedFromSerial(byte[] data);

        void onFlashProcess(int totalLength, int flashedLength);
    }
}
