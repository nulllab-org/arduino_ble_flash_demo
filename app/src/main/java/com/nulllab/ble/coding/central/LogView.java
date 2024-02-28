package com.nulllab.ble.coding.central;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.nulllab.ble.coding.central.util.MainThreadUtils;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.Semaphore;

public class LogView extends androidx.appcompat.widget.AppCompatTextView {

    private static final String TAG = "LogView";
    private static final int MAX_LENGTH = 2048;

    private final SimpleDateFormat sSimpleDateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final StringBuilder mStringBuilder = new StringBuilder();
    private final Semaphore mSemaphore = new Semaphore(0);
    private boolean mUpdate = false;

    public LogView(Context context) {
        super(context);
        setMovementMethod(ScrollingMovementMethod.getInstance());
        init();
    }

    public LogView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setMovementMethod(ScrollingMovementMethod.getInstance());
        init();
    }

    public LogView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setMovementMethod(ScrollingMovementMethod.getInstance());
        init();
    }

    private static String binToHexString(byte[] data) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : data) {
            stringBuilder.append(String.format("%02X ", b));
        }

        return stringBuilder.toString().trim();
    }

    private void init() {
        new Thread(() -> {
            while (true) {
                update();
            }
        }).start();
    }

    private void update() {
        String text = null;
        synchronized (this) {
            while (!mUpdate) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            text = mStringBuilder.toString();
            mUpdate = false;
        }

        final String final_text = text;

        MainThreadUtils.run(() -> {
            setText(final_text);
            int offset = getLineCount() * getLineHeight();
            if (offset > getHeight()) {
                scrollTo(0, offset - getHeight());
            }
            mSemaphore.release();
        });

        try {
            mSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void append(byte[] data) {
        mStringBuilder.append(new String(data));
        if (mStringBuilder.length() > MAX_LENGTH) {
            mStringBuilder.delete(0, mStringBuilder.length() - MAX_LENGTH);
        }
        mUpdate = true;
        notify();
    }

    public synchronized void write(CharSequence string) {
        mStringBuilder.append(string);
        if (mStringBuilder.length() > MAX_LENGTH) {
            mStringBuilder.delete(0, mStringBuilder.length() - MAX_LENGTH);
        }
        mUpdate = true;
        notify();
    }
}
