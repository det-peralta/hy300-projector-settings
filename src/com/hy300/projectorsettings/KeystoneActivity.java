package com.hy300.projectorsettings;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Full-screen 4-corner keystone editor for the remote.
 * D-pad moves the selected corner, OK selects the next corner, hold OK resets, BACK saves and exits.
 */
public class KeystoneActivity extends Activity {
    private static final String[] CORNER_NAMES = {"Top-left", "Top-right", "Bottom-left", "Bottom-right"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Shell sh;
    private int[] v = new int[8];   // ltx lty rtx rty lbx lby rbx rby
    private int w = 1280, h = 720;  // panel pixels
    private int corner = 0;
    private boolean loaded;
    private Grid grid;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sh = Shell.get(this);
        grid = new Grid(this);
        setContentView(grid);
        new Thread(() -> {
            int[] s = Keystone.screen(sh);
            int[] cur = Keystone.current(sh);
            boolean auto = sh.getBool(Keystone.PROP_AUTO);
            if (auto) sh.setProp(Keystone.PROP_AUTO, "0"); // auto keystone would fight manual edits
            ui.post(() -> {
                w = s[0];
                h = s[1];
                v = cur;
                loaded = true;
                if (auto) Toast.makeText(this, "Auto keystone turned off", Toast.LENGTH_SHORT).show();
                grid.invalidate();
            });
        }).start();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (!loaded) return super.onKeyDown(code, e);
        int step = Math.min(1 + e.getRepeatCount() / 2, 8);
        int xi = corner * 2, yi = xi + 1;
        boolean left = corner == 0 || corner == 2, top = corner < 2;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(xi, left ? step : -step); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:  move(xi, left ? -step : step); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  move(yi, top ? step : -step); return true;
            case KeyEvent.KEYCODE_DPAD_UP:    move(yi, top ? -step : step); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (e.getRepeatCount() == 0) {
                    e.startTracking();
                } else if (e.getRepeatCount() == 15) {
                    v = new int[8];
                    apply();
                    Toast.makeText(this, "Keystone reset", Toast.LENGTH_SHORT).show();
                }
                return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && loaded) {
            if (!e.isCanceled() && (e.getFlags() & KeyEvent.FLAG_LONG_PRESS) == 0 && e.getEventTime() - e.getDownTime() < 800) {
                corner = (corner + 1) % 4;
                grid.invalidate();
            }
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent e) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
    }

    private void move(int i, int delta) {
        boolean isX = i % 2 == 0;
        int limit = (int) ((isX ? w : h) * 0.6f);
        int partner = isX ? (i ^ 2) : (i < 4 ? i + 4 : i - 4); // corner on the same edge
        int nv = Math.max(0, v[i] + delta);
        nv = Math.min(nv, limit - v[partner]);
        if (nv == v[i]) return;
        v[i] = nv;
        apply();
    }

    private void apply() {
        Keystone.saveManual(this, v);
        sh.keystoneAsync(v);
        grid.invalidate();
    }

    private static final class Grid extends View {
        private final KeystoneActivity a;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);

        Grid(KeystoneActivity c) {
            super(c);
            a = c;
            setBackgroundColor(MainActivity.BG);
            setFocusable(true);
            line.setColor(0x40FFFFFF);
            line.setStrokeWidth(2);
            border.setColor(0xFFFFFFFF);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(8);
            dot.setColor(0xFF9AA4AF);
            sel.setColor(0xFFFF9F1C);
            txt.setColor(MainActivity.TEXT);
            txt.setTextAlign(Paint.Align.CENTER);
            small.setColor(MainActivity.DIM);
            small.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight();
            for (int k = 1; k < 8; k++) {
                c.drawLine(W * k / 8, 0, W * k / 8, H, line);
                c.drawLine(0, H * k / 8, W, H * k / 8, line);
            }
            c.drawRect(4, 4, W - 4, H - 4, border);
            float r = H / 30f;
            float[][] pos = {{r * 2, r * 2}, {W - r * 2, r * 2}, {r * 2, H - r * 2}, {W - r * 2, H - r * 2}};
            for (int k = 0; k < 4; k++) c.drawCircle(pos[k][0], pos[k][1], k == a.corner ? r * 1.4f : r, k == a.corner ? sel : dot);

            txt.setTextSize(H / 16f);
            small.setTextSize(H / 30f);
            if (!a.loaded) {
                c.drawText("Loading…", W / 2, H / 2, txt);
                return;
            }
            c.drawText(CORNER_NAMES[a.corner] + "   " + a.v[a.corner * 2] + ", " + a.v[a.corner * 2 + 1], W / 2, H / 2 - H / 20f, txt);
            c.drawText("Arrows: move corner    OK: next corner    Hold OK: reset    Back: done",
                    W / 2, H / 2 + H / 20f, small);
        }
    }
}
