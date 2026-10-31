package com.hy300.projectorsettings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final int BG = 0xFF101418, CARD = 0xFF1B2128, FOCUS = 0xFF2F6FED, TEXT = 0xFFECEFF3, DIM = 0xFF9AA4AF;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Shell sh;
    private LinearLayout list;
    private final List<Toggle> toggles = new ArrayList<>();

    private static final class Toggle {
        final String prop;
        TextView value;
        boolean on;
        Toggle(String prop) { this.prop = prop; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sh = Shell.get(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(40);
        list.setPadding(pad * 2, pad, pad * 2, pad);
        scroll.addView(list);
        setContentView(scroll);

        TextView title = text("Projector", 30, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        list.addView(title);
        TextView sub = text("Keystone, focus and sensor settings", 15, DIM);
        sub.setPadding(0, dp(4), 0, dp(20));
        list.addView(sub);

        View manual = action("Manual keystone", "Adjust the four corners", () ->
                startActivity(new Intent(this, KeystoneActivity.class)));
        toggle("Auto keystone", "Correct the picture automatically when the projector is moved", Keystone.PROP_AUTO);
        toggle("Autofocus", "Refocus automatically after movement", "persist.sys.vafocus");
        toggle("Auto four-corner correction", "Camera-assisted corner correction", "persist.sys.tpryxcrt");
        toggle("Screen recognition", "Fit the picture to a detected screen", "persist.sys.mbrecognize");
        toggle("Obstacle avoidance", "Shrink the picture around obstacles", "persist.sys.obstacle");
        toggle("Anti-shake", "Ignore small bumps (gyro sensor)", "hotack.sensor.anti_shake");
        action("Focus calibration", "Run the vendor focus calibration", () -> {
            try {
                startActivity(new Intent().setComponent(
                        new ComponentName("com.hysd.vafocus", "com.hysd.vafocus.VajzActivity")));
            } catch (Exception e) {
                toast("Focus app not available");
            }
        });
        action("Reset keystone", "Remove all corner correction", () -> new Thread(() -> {
            int[] zero = new int[8];
            Keystone.saveManual(this, zero);
            String r = sh.keystone(zero);
            ui.post(() -> toast(r.startsWith("ok") ? "Keystone reset" : "Failed: " + r));
        }).start());

        manual.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        new Thread(() -> {
            for (Toggle t : toggles) t.on = sh.getBool(t.prop);
            ui.post(this::render);
        }).start();
    }

    private void render() {
        for (Toggle t : toggles) {
            t.value.setText(t.on ? "On" : "Off");
            t.value.setTextColor(t.on ? 0xFF6FD08C : DIM);
        }
    }

    private void toggle(String label, String desc, String prop) {
        Toggle t = new Toggle(prop);
        toggles.add(t);
        LinearLayout row = row(label, desc);
        t.value = text("…", 18, DIM);
        row.addView(t.value);
        row.setOnClickListener(v -> {
            boolean on = !t.on;
            t.on = on;
            render();
            new Thread(() -> {
                sh.setProp(prop, on ? "1" : "0");
                if (prop.equals(Keystone.PROP_AUTO)) {
                    if (on) {
                        // same broadcast the stock launcher sends when enabling auto keystone
                        sendBroadcast(new Intent("android.intent.hotack_keystone")
                                .putExtra("ratio", 1).putExtra("keystone", 1));
                    } else {
                        sh.keystone(Keystone.savedManual(this)); // restore manual corners
                    }
                }
                t.on = sh.getBool(prop);
                ui.post(this::render);
            }).start();
        });
    }

    private View action(String label, String desc, Runnable r) {
        LinearLayout row = row(label, desc);
        row.addView(text("›", 26, DIM));
        row.setOnClickListener(v -> r.run());
        return row;
    }

    private LinearLayout row(String label, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(24), dp(14), dp(24), dp(14));
        row.setBackground(rowBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        list.addView(row, lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(label, 20, TEXT));
        TextView d = text(desc, 14, DIM);
        d.setDuplicateParentStateEnabled(true);
        d.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFFDCE6FF, DIM}));
        texts.setDuplicateParentStateEnabled(true);
        texts.addView(d);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    private StateListDrawable rowBg() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(FOCUS));
        s.addState(new int[]{}, rounded(CARD));
        return s;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
