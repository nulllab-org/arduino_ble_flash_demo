# 示例APK：蓝牙传输HEX到BLE-UNO

## 步骤

- 主机通过蓝牙BLE模式连接到BLE-UNO的服务UUID: `0000ffe0-0000-1000-8000-00805f9b34fb`,设备蓝牙名字默认为`ble-uno4.2`, 其中串口的字符UUID为`0000ffe1-0000-1000-8000-00805f9b34fb`,AT指令的字符UUID为`0000ffe2-0000-1000-8000-00805f9b34fb`

- 往`0000ffe2-0000-1000-8000-00805f9b34fb`写入`AT+TARGE_RESET\r\n`重置BLE-UNO

- 往`0000ffe1-0000-1000-8000-00805f9b34fb`写入按照skt500v1协议写入hex转成的bin文件

## 联系方式

若有任何问题，请通过邮箱联系我：arex@null-lab.com
