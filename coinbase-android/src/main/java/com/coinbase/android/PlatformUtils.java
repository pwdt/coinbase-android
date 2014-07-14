package com.coinbase.android;

import android.os.Build;

public class PlatformUtils {

    private static final int SDK = Build.VERSION.SDK_INT;

    private PlatformUtils() {
        // Uninstantiable
    }

    public static boolean hasKitKat() {
        return SDK >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean hasJellybeanMR1() {
        return SDK >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    public static boolean hasJellybean() {
        return SDK >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean hasIceCreamSandwich() {
        return SDK >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean hasHoneycomb() {
        return SDK >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean hasGingerbread() {
        return SDK >= Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean hasFroyo() {
        return SDK >= Build.VERSION_CODES.FROYO;
    }
}
