package studio.qiusui.perfticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** 状态栏跑马灯快讯。 */
final class Marquee extends View {

    private String text = "";
    private long t0 = SystemClock.elapsedRealtime();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float speed = Ui.dp(46);
    private final float gap = Ui.dp(28);

    Marquee(Context c) {
        super(c);
        p.setTextSize(Ui.dp(10.5f));
        p.setTypeface(Theme.mono);
    }

    void setText(String s) {
        if (s == null) s = "";
        if (!s.equals(text)) {
            text = s;
            t0 = SystemClock.elapsedRealtime();
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        p.setColor(Theme.textDim);
        float tw = p.measureText(text);
        float baseline = h / 2f - (p.descent() + p.ascent()) / 2f;

        if (tw + gap <= w || tw <= 0) {
            canvas.drawText(text, 0, baseline, p);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        float offset = ((now - t0) / 1000f) * speed % (tw + gap);
        canvas.drawText(text, -offset, baseline, p);
        canvas.drawText(text, -offset + tw + gap, baseline, p);
        postInvalidateDelayed(16);
    }
}
