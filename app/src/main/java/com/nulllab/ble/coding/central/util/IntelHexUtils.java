package com.nulllab.ble.coding.central.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class IntelHexUtils {
    public static byte[] hexToBin(BufferedReader reader) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        String line;
        while ((line = reader.readLine()) != null) {
//            System.out.println(line);
            if (line.charAt(0) != ':') {
                throw new IllegalArgumentException("line must start with ':', but it is " + line.charAt(0));
            }

            byte[] lineBytes = HexStringToBytes(line.substring(1));
            int length = Byte.toUnsignedInt(lineBytes[0]);
            int address = (Byte.toUnsignedInt(lineBytes[1]) << 8) | Byte.toUnsignedInt(lineBytes[2]);
            int type = Byte.toUnsignedInt(lineBytes[3]);

//            System.out.print("total length: " + lineBytes.length);
//            System.out.print(", data length: " + length);
//            System.out.print(", address: " + address);
//            System.out.print(", type: " + type);
//            System.out.println(", checksum: "
//                    + Integer.toHexString(Byte.toUnsignedInt(lineBytes[lineBytes.length - 1])));

            byte checksum = 0;
            for (byte b : lineBytes) {
                checksum += b;
            }

            if (checksum != 0) {
                throw new IllegalArgumentException("error checksum");
            }
            if (type == 0) {
                byteArrayOutputStream.write(lineBytes, 4, length);
            }
        }

        return byteArrayOutputStream.toByteArray();
    }

    private static byte[] HexStringToBytes(String hexString) {
        // 检查hexString是否为空
        if (hexString == null || hexString.isEmpty()) {
            throw new IllegalArgumentException("Hex string is empty");
        }

        int length = hexString.length();

        // 检查hexString的长度是否为偶数
        if ((length & 0x01) != 0) {
            throw new IllegalArgumentException("Hex string length must be even");
        }

        // 检查hexString是否只包含十六进制数字字符
        for (int i = 0; i < length; i++) {
            if (!Character.isDigit(hexString.charAt(i))
                    && (hexString.charAt(i) < 'a' || hexString.charAt(i) > 'f')
                    && (hexString.charAt(i) < 'A' || hexString.charAt(i) > 'F')) {
                throw new IllegalArgumentException("Hex string contains non-hexadecimal characters");
            }
        }

        byte[] data = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            data[i >> 1] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
}
