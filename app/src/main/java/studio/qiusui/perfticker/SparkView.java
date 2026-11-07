package studio.qiusui.perfticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

/** 迷你 K 线：自选行 / 指数卡里的一列小蜡烛。 */
final class SparkView extends View {

    private Ticker ticker;
    private int periodIdx = 1;
    private int maxBars = 26;

    private final Paint upPaint = new Paint();
    private final Paint downPaint = new Paint();
    private final Paint flatPaint = new Paint();
    private final Paint basePaint = new Paint();
    private final RectF rect = new RectF();

    SparkView(Context c) {
        super(c);
        init();
    }

    SparkView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        upPaint.setStyle(Paint.Style.FILL);
        upPaint.setAntiAlias(false);
        downPaint.setStyle(Paint.Style.FILL);
        downPaint.setAntiAlias(false);
        flatPaint.setStyle(Paint.Style.FILL);
        flatPaint.setAntiAlias(false);
        basePaint.setStyle(Paint.Style.FILL);
    }

    void setTicker(Ticker t) {
        ticker = t;
    }

    void setPeriod(int idx) {
        periodIdx = idx;
    }

    void setMaxBars(int n) {
        maxBars = n;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (ticker == null) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 2 || h <= 2) return;

        upPaint.setColor(Theme.UP);
        downPaint.setColor(Theme.DN);
        flatPaint.setColor(Theme.flat);
        basePaint.setColor(Theme.alpha(Theme.line, 90));
        canvas.drawRect(0, 0, w, h, basePaint);

        int idx = periodIdx;
        if (idx < 0 || idx >= Ticker.PERIODS.length) idx = 0;
        ArrayList<Candle> bars = ticker.bars(idx, maxBars);
        if (bars.isEmpty()) return;

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < bars.size(); i++) {
            Candle c = bars.get(i);
            if (c.h > max) max = c.h;
            if (c.l < min) min = c.l;
        }
        if (max - min < 1e-9) {
            max += 0.5;
            min -= 0.5;
        }

        float step = w / (float) bars.size();
        float bodyW = Math.max(1.5f, step * 0.62f);
        float top = 1f;
        float bottom = h - 1f;

        for (int i = 0; i < bars.size(); i++) {
            Candle c = bars.get(i);
            float cx = step * i + step / 2f;
            float yH = bottom - (float) ((c.h - min) / (max - min)) * (bottom - top);
            float yL = bottom - (float) ((c.l - min) / (max - min)) * (bottom - top);
            float yO = bottom - (float) ((c.o - min) / (max - min)) * (bottom - top);
            float yC = bottom - (float) ((c.c - min) / (max - min)) * (bottom - top);

            Paint p = c.bull() ? upPaint : downPaint;
            if (Math.abs(c.c - c.o) < 1e-9 && Math.abs(c.h - c.l) < 1e-9) p = flatPaint;

            // 影线
            float lx = cx - 0.5f;
            canvas.drawRect(lx, yH, lx + 1f, yL + 0.6f, p);

            float bTop = Math.min(yO, yC);
            float bBot = Math.max(yO, yC);
            if (bBot - bTop < 1f) bBot = bTop + 1f;
            rect.set(cx - bodyW / 2f, bTop, cx + bodyW / 2f, bBot);
            canvas.drawRect(rect, p);
        }
    }
}
