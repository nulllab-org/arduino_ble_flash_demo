[中文版](README_CN.md)

# Sample APK: Bluetooth Transmission of HEX to BLE-UNO

## Steps

- The host connects to the BLE-UNO service UUID `0000ffe0-0000-1000-8000-00805f9b34fb` in Bluetooth BLE mode, with the device Bluetooth name defaulting to `ble-uno4.2`. The UUID for the serial port character is `0000ffe1-0000-1000-8000-00805f9b34fb`, and the UUID for the AT command character is `0000ffe2-0000-1000-8000-00805f9b34fb`.

- Write `AT+TARGE_RESET\r\n` to `0000ffe2-0000-1000-8000-00805f9b34fb` to reset the BLE-UNO.

- Write the bin file converted from hex according to the skt500v1 protocol to `0000ffe1-0000-1000-8000-00805f9b34fb`.

## Contact Information

For any inquiries, please contact me via email at: arex@null-lab.com
