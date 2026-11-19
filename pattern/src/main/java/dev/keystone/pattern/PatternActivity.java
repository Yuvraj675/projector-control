package dev.keystone.pattern;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Full-screen keystone test pattern: black background, four white circle markers at fixed fractions of the
 * frame (MARKERS), a frame border and a faint grid. The marker centres are the points the phone-side
 * solver detects; keep MARKERS in sync with Panel.markerFractions (core/Keystone.kt; a unit test checks it).
 */
public class PatternActivity extends Activity {
    // Marker centres as fractions of (width, height): TL, TR, BR, BL. BR sits left of the corner so the
    // firmware's mute indicator (bottom right, drawn inside the warped frame) never covers it.
    static final float[][] MARKERS = {{0.075f, 0.075f}, {0.925f, 0.075f}, {0.85f, 0.925f}, {0.075f, 0.925f}};
    static final float MARKER_RADIUS_FRAC = 0.055f; // radius as a fraction of height

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View v = new PatternView(this);
        v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(v);
    }

    static class PatternView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

        PatternView(Context c) {
            super(c);
            fill.setColor(Color.WHITE);
            fill.setStyle(Paint.Style.FILL);
            line.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            c.drawColor(Color.BLACK);
            // faint grid every 10 %
            line.setColor(Color.rgb(60, 60, 60));
            line.setStrokeWidth(1f);
            for (int i = 1; i < 10; i++) {
                c.drawLine(w * i / 10f, 0, w * i / 10f, h, line);
                c.drawLine(0, h * i / 10f, w, h * i / 10f, line);
            }
            // centre cross
            line.setColor(Color.rgb(120, 120, 120));
            c.drawLine(w / 2 - 30, h / 2, w / 2 + 30, h / 2, line);
            c.drawLine(w / 2, h / 2 - 30, w / 2, h / 2 + 30, line);
            // frame border, inset so it is never clipped
            line.setColor(Color.WHITE);
            line.setStrokeWidth(4f);
            c.drawRect(2, 2, w - 2, h - 2, line);
            // four markers
            float r = h * MARKER_RADIUS_FRAC;
            for (float[] m : MARKERS) c.drawCircle(w * m[0], h * m[1], r, fill);
        }
    }
}
