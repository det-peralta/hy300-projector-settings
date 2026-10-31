package com.hy300.projectorsettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Runs as its own process via `app_process` (started by {@link Shell}), outside the
 * app's hidden-API sandbox. Line protocol on stdin/stdout, one reply line per command:
 *
 *   get <prop>                        -> ok <value>
 *   set <prop> <value>                -> ok
 *   ks ltx lty rtx rty lbx lby rbx rby -> ok   (corner insets in panel pixels)
 *
 * "ks" does the same thing as the stock launcher: IDisplayConfig transaction 36
 * (two zoom floats + four corner points TL, TR, BL, BR) and then persists the insets in
 * persist.display.keystone_* so the display stack restores them at boot.
 */
public class Helper {
    private static final String IFACE = "vendor.display.config@1.0::IDisplayConfig";
    private static final String[] KS_KEYS = {"ltx", "lty", "rtx", "rty", "lbx", "lby", "rbx", "rby"};

    private static Method spGet, spSet;

    public static void main(String[] args) throws Exception {
        Class<?> sp = Class.forName("android.os.SystemProperties");
        spGet = sp.getMethod("get", String.class, String.class);
        spSet = sp.getMethod("set", String.class, String.class);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("ready");
        System.out.flush();
        String line;
        while ((line = in.readLine()) != null) {
            String reply;
            try {
                reply = handle(line.trim().split("\\s+"));
            } catch (Throwable t) {
                Throwable c = (t instanceof InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
                reply = "err " + c;
            }
            System.out.println(reply.replace('\n', ' '));
            System.out.flush();
        }
    }

    private static String handle(String[] a) throws Exception {
        switch (a[0]) {
            case "get":
                return "ok " + spGet.invoke(null, a[1], "");
            case "set":
                spSet.invoke(null, a[1], a.length > 2 ? a[2] : "");
                return "ok";
            case "ks":
                int[] v = new int[8];
                for (int i = 0; i < 8; i++) v[i] = Math.abs(Integer.parseInt(a[i + 1]));
                return keystone(v);
            default:
                return "err unknown command";
        }
    }

    private static String keystone(int[] v) throws Exception {
        String[] wh = ((String) spGet.invoke(null, "persist.vendor.disp.screensize", "1920x1080")).split("x");
        int w = Integer.parseInt(wh[0]) - 1, h = Integer.parseInt(wh[1]) - 1;
        int[][] pts = {
                {v[0], v[1]},          // top-left
                {w - v[2], v[3]},      // top-right
                {v[4], h - v[5]},      // bottom-left
                {w - v[6], h - v[7]},  // bottom-right
        };

        Class<?> hb = Class.forName("android.os.HwBinder");
        Class<?> hp = Class.forName("android.os.HwParcel");
        Class<?> blob = Class.forName("android.os.HwBlob");
        Class<?> ib = Class.forName("android.os.IHwBinder");
        Object svc = hb.getMethod("getService", String.class, String.class).invoke(null, IFACE, "default");
        if (svc == null) return "err display config service not found";

        Object req = hp.getConstructor().newInstance();
        Object rep = hp.getConstructor().newInstance();
        hp.getMethod("writeInterfaceToken", String.class).invoke(req, IFACE);
        Method writeFloat = hp.getMethod("writeFloat", float.class);
        writeFloat.invoke(req, 100f);
        writeFloat.invoke(req, 100f);
        Method put = blob.getMethod("putInt32", long.class, int.class);
        Method writeBuffer = hp.getMethod("writeBuffer", blob);
        for (int[] p : pts) {
            Object b = blob.getConstructor(int.class).newInstance(8);
            put.invoke(b, 0L, p[0]);
            put.invoke(b, 4L, p[1]);
            writeBuffer.invoke(req, b);
        }
        try {
            ib.getMethod("transact", int.class, hp, hp, int.class).invoke(svc, 36, req, rep, 0);
            hp.getMethod("verifySuccess").invoke(rep);
            hp.getMethod("readInt32").invoke(rep);
        } finally {
            hp.getMethod("release").invoke(rep);
        }

        spSet.invoke(null, "persist.display.keystone_zoom_h", "100.0");
        spSet.invoke(null, "persist.display.keystone_zoom_v", "100.0");
        for (int i = 0; i < 8; i++) spSet.invoke(null, "persist.display.keystone_" + KS_KEYS[i], String.valueOf(v[i]));
        return "ok";
    }
}
