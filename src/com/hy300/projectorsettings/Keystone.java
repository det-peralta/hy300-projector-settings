package com.hy300.projectorsettings;

import android.content.Context;
import android.content.SharedPreferences;

/** Corner insets in panel pixels, order: ltx lty rtx rty lbx lby rbx rby. */
public final class Keystone {
    public static final String[] KEYS = {"ltx", "lty", "rtx", "rty", "lbx", "lby", "rbx", "rby"};
    public static final String PROP_AUTO = "persist.sys.tpryauto";

    private Keystone() {}

    /** Values currently applied (written by us, the stock launcher, or auto keystone). */
    public static int[] current(Shell sh) {
        int[] v = new int[8];
        for (int i = 0; i < 8; i++) v[i] = parse(sh.getProp("persist.display.keystone_" + KEYS[i]));
        return v;
    }

    /** Panel size, e.g. {1280, 720}. */
    public static int[] screen(Shell sh) {
        String[] wh = sh.getProp("persist.vendor.disp.screensize").split("x");
        if (wh.length == 2) return new int[]{parse(wh[0]), parse(wh[1])};
        return new int[]{1920, 1080};
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("keystone", Context.MODE_PRIVATE);
    }

    /** Last manual correction, re-applied when auto keystone is switched off. */
    public static int[] savedManual(Context c) {
        SharedPreferences p = prefs(c);
        int[] v = new int[8];
        for (int i = 0; i < 8; i++) v[i] = p.getInt(KEYS[i], 0);
        return v;
    }

    public static void saveManual(Context c, int[] v) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int i = 0; i < 8; i++) e.putInt(KEYS[i], v[i]);
        e.apply();
    }

    private static int parse(String s) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return 0;
        }
    }
}
