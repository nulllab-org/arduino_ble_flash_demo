# Android BLE Coding Central

[![Android CI](https://github.com/nulllab-org/android_ble_coding_central/actions/workflows/android_ci.yml/badge.svg)](https://github.com/nulllab-org/android_ble_coding_central/actions/workflows/android_ci.yml) [![Android Release](https://github.com/nulllab-org/android_ble_coding_central/actions/workflows/android_release.yml/badge.svg)](https://github.com/nulllab-org/android_ble_coding_central/actions/workflows/android_release.yml)

## Overview

This is an Android Demo APK project that includes the functionality of transmitting Micropython Python code to ESP32 slave devices via Bluetooth for wireless programming. The application also monitors the serial communication of the ESP32, enabling remote wireless serial data transmission. This application requires the use of specific ESP32 boards and firmware in order to function properly.

## Getting Started

### Compilation

After downloading the project, it is necessary to update the submodules. Please use the following command:

```shell
 git submodule update --init --recursive
```

Once the update is complete, you can proceed with compiling the project using Android Studio.

### Code Transfer

- After installing the APK on your phone, make sure that Bluetooth is enabled.

- Power on the ESP32 board. When the Bluetooth LED on the board lights up and then goes off, it indicates that no host is connected to it.

- On the app interface, click on "scan device". This will search for all Bluetooth devices with the name "ble_coding_peripheral" and display their Bluetooth MAC addresses. If there is only one ESP32 device nearby, you can select its unique MAC address.

- After selecting the device, toggle the "connect" switch on the interface to establish the connection. Once connected, the "State" will display "Connected", and the ESP32's blue LED light will stay on.

- With the connection established, you can proceed with code transfer. There are some preloaded "demo code" available. You can select the "print" option in the "demo code" section and click "send main.py".

- Once the file transfer is complete, the application will display "file transmitted", and the ESP32 board will automatically soft reboot and run the new program.

- If the "print" program runs successfully, you will see continuous printing messages in the serial output log.

## Additional Notes

- The code transfer process primarily uses the ble standard API provided by Android for connecting and data transfer.

- To read serial input, you can refer to the example code "stdio". Use `sys.stdin.readline()` to read serial input, which will block until it reads a complete line of serial data (terminated with '\n'). Therefore, when sending a line of serial data from the APK to the ESP32, make sure to include a newline character at the end, similar to pressing the enter key on the keyboard.

- Due to the power consumption involved in Bluetooth device scanning, different smartphone manufacturers have implemented varying restrictions on Bluetooth scanning. For example, some phones may not support frequent or long-duration scanning. Therefore, once you have found the desired Bluetooth device, you can stop the scanning process and select the device from the Bluetooth address dropdown. If you already know the MAC address of the ESP32 in advance, you can skip the scanning process and directly connect using the MAC address.

- If the ESP32 is already connected to another host device, the blue signal LED will stay on. In this case, it will not be discoverable by other host devices or connect to them.
