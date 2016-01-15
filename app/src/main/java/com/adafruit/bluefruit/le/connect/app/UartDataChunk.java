package com.adafruit.bluefruit.le.connect.app;

public class UartDataChunk {
    static final int TRANSFERMODE_TX = 0;
    static final int TRANSFERMODE_RX = 1;

    private long mTimestamp;        // in millis
    private int mMode;
    private String mData;

    public UartDataChunk(long timestamp, int mode, String data) {
        mTimestamp = timestamp;
        mMode = mode;
        mData = data;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public int getMode() {
        return mMode;
    }

    public String getData() {
        return mData;
    }
}
