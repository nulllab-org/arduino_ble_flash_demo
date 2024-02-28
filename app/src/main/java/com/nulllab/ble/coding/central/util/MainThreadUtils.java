package com.nulllab.ble.coding.central.util;

import android.os.Handler;
import android.os.Looper;

public class MainThreadUtils {
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    public static void run(Runnable runnable) {
        if (Thread.currentThread() == sHandler.getLooper().getThread()) {
            runnable.run();
        } else {
            sHandler.post(runnable);
        }
    }

    public static boolean inMainThread() {
        return Thread.currentThread() == sHandler.getLooper().getThread();
    }
}
