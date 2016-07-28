package space.potatofrom.aeiodine;

import android.content.Intent;
import android.util.Log;

public class IodineClient {
    public static final String TAG = "NATIVE";

    public static native int getDnsFd();

    public static native int connect(String nameserv_addr, String topdomain, boolean raw_mode, boolean lazy_mode,
                                     String password, int request_hostname_size, int response_fragment_size);

    public static native String getIp();

    public static native String getRemoteIp();

    public static native int getNetbits();

    public static native int getMtu();

    public static native int tunnel(int fd);

    public static native void tunnelInterrupt();

    public static native String getPropertyNetDns1();

    static {
        System.loadLibrary("iodine-client");
        Log.d(TAG, "Native Library iodine-client loaded");
    }
}