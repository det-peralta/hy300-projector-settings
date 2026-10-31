package com.hy300.projectorsettings;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Talks to a long-lived {@link Helper} process started with app_process from our own APK. */
public final class Shell {
    private static final String TAG = "ProjectorSettings";
    private static Shell instance;

    private final String apk;
    private Process proc;
    private Writer out;
    private BufferedReader in;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private int[] pendingKs;

    private Shell(Context c) {
        apk = c.getApplicationInfo().sourceDir;
    }

    public static synchronized Shell get(Context c) {
        if (instance == null) instance = new Shell(c.getApplicationContext());
        return instance;
    }

    private void start() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("/system/bin/app_process", "/system/bin",
                Helper.class.getName());
        pb.environment().put("CLASSPATH", apk);
        pb.redirectErrorStream(true);
        proc = pb.start();
        out = new OutputStreamWriter(proc.getOutputStream());
        in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String l;
        while ((l = in.readLine()) != null && !l.equals("ready")) Log.w(TAG, "helper: " + l);
        if (l == null) throw new IllegalStateException("helper exited during startup");
    }

    /** Sends one command and returns the reply line ("ok ..." / "err ..."). Blocking. */
    public synchronized String exec(String cmd) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (proc == null) start();
                out.write(cmd + "\n");
                out.flush();
                String r;
                // skip any stray runtime log lines that are not replies
                while ((r = in.readLine()) != null && !r.startsWith("ok") && !r.startsWith("err")) {
                    Log.w(TAG, "helper: " + r);
                }
                if (r == null) throw new IllegalStateException("helper died");
                if (r.startsWith("err")) Log.e(TAG, cmd + " -> " + r);
                return r;
            } catch (Exception e) {
                Log.e(TAG, "helper failure", e);
                if (proc != null) proc.destroy();
                proc = null;
            }
        }
        return "err helper unavailable";
    }

    public String getProp(String key) {
        String r = exec("get " + key);
        return r.startsWith("ok ") ? r.substring(3).trim() : "";
    }

    public boolean getBool(String key) {
        String v = getProp(key);
        return v.equals("1") || v.equalsIgnoreCase("true");
    }

    public void setProp(String key, String value) {
        exec("set " + key + " " + value);
    }

    /** Applies keystone asynchronously; rapid calls are coalesced so only the newest is sent. */
    public void keystoneAsync(int[] v) {
        synchronized (bg) {
            boolean queued = pendingKs != null;
            pendingKs = v.clone();
            if (queued) return;
        }
        bg.execute(() -> {
            int[] p;
            synchronized (bg) {
                p = pendingKs;
                pendingKs = null;
            }
            keystone(p);
        });
    }

    public String keystone(int[] v) {
        StringBuilder sb = new StringBuilder("ks");
        for (int x : v) sb.append(' ').append(x);
        return exec(sb.toString());
    }

    public void warmUp() {
        bg.execute(() -> exec("get ro.product.model"));
    }
}
