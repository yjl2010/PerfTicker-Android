package studio.qiusui.perfticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * 主图：K 线 + MA5/10/20 + 成交量 + 十字光标 / OHLC 浮窗。
 * 对应原工程 ChartPanel.cs。
 */
final class ChartView extends View {

    private Ticker ticker;
    private int periodIdx = 1;
    private int touchIdx = -1;

    private final ArrayList<Candle> drawn = new ArrayList<Candle>();
    private final RectF plot = new RectF();
    private final RectF volRect = new RectF();
    private final RectF boxRect = new RectF();
    private float stepX = 1f;
    private float firstX = 0f;
    private float bodyW = 2f;
    private double visMin = 0;
    private double visMax = 1;
    private double volMax = 1;

    private final Paint bgP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barP = new Paint();
    private final Paint wickP = new Paint();
    private final Paint volP = new Paint();
    private final Paint ma5P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ma10P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ma20P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxBgP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxTextP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markP = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    ChartView(Context c) {
        super(c);
        init();
    }

    ChartView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        gridP.setStyle(Paint.Style.STROKE);
        gridP.setStrokeWidth(Ui.dp(0.6f));
        axisP.setTextSize(Ui.dp(9f));
        axisP.setTypeface(Theme.mono);
        barP.setStyle(Paint.Style.FILL);
        wickP.setStyle(Paint.Style.FILL);
        volP.setStyle(Paint.Style.FILL);
        ma5P.setStyle(Paint.Style.STROKE);
        ma5P.setStrokeWidth(Ui.dp(1.1f));
        ma10P.setStyle(Paint.Style.STROKE);
        ma10P.setStrokeWidth(Ui.dp(1.1f));
        ma20P.setStyle(Paint.Style.STROKE);
        ma20P.setStrokeWidth(Ui.dp(1.1f));
        legendP.setTextSize(Ui.dp(9.5f));
        legendP.setTypeface(Theme.mono);
        crossP.setStyle(Paint.Style.STROKE);
        crossP.setStrokeWidth(Ui.dp(0.8f));
        crossP.setPathEffect(new DashPathEffect(new float[]{Ui.dp(3), Ui.dp(3)}, 0));
        boxBgP.setStyle(Paint.Style.FILL);
        boxTextP.setTextSize(Ui.dp(9.5f));
        boxTextP.setTypeface(Theme.mono);
        markP.setTextSize(Ui.dp(9f));
        markP.setTypeface(Theme.monoBold);
        setClickable(true);
    }

    void setTicker(Ticker t) {
        ticker = t;
        touchIdx = -1;
        invalidate();
    }

    void setPeriod(int idx) {
        periodIdx = idx;
        touchIdx = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        bgP.setColor(Theme.panel);
        canvas.drawRect(0, 0, w, h, bgP);

        if (ticker == null) return;
        Instr ins = ticker.instr;

        float axisW = Ui.dp(46);
        float volH = Math.max(Ui.dp(40), h * 0.22f);
        float timeRow = Ui.dp(13);
        plot.set(Ui.dp(4), Ui.dp(19), w - axisW, h - volH - timeRow - Ui.dp(8));
        volRect.set(Ui.dp(4), plot.bottom + Ui.dp(15), w - axisW, h - timeRow - Ui.dp(2));

        ArrayList<Candle> all = ticker.bars(periodIdx, 200);
        int maxBars = (int) (plot.width() / Ui.dp(7.2f));
        if (maxBars < 16) maxBars = 16;
        if (maxBars > 64) maxBars = 64;

        drawn.clear();
        if (all.isEmpty()) {
            drawEmpty(canvas, ins);
            return;
        }
        int start = Math.max(0, all.size() - maxBars);
        for (int i = start; i < all.size(); i++) drawn.add(all.get(i));

        visMin = Double.MAX_VALUE;
        visMax = -Double.MAX_VALUE;
        volMax = 0;
        for (int i = 0; i < drawn.size(); i++) {
            Candle c = drawn.get(i);
            if (c.l < visMin) visMin = c.l;
            if (c.h > visMax) visMax = c.h;
            if (c.v > volMax) volMax = c.v;
        }
        if (visMax - visMin < 1e-9) {
            visMax += 0.5;
            visMin -= 0.5;
        } else {
            double pad = (visMax - visMin) * 0.06;
            visMax += pad;
            visMin -= pad;
        }

        stepX = plot.width() / (float) maxBars;
        firstX = plot.left + stepX * (maxBars - drawn.size());
        bodyW = Math.max(1.6f, stepX * 0.62f);

        drawGrid(canvas, ins);
        drawWatermark(canvas);
        drawVolume(canvas, ins);
        drawCandles(canvas);
        drawMAs(canvas, all, start, ins);
        drawLegend(canvas, all, start, ins);
        drawTimeAxis(canvas);
        drawLastMark(canvas, ins);
        if (touchIdx >= 0 && touchIdx < drawn.size()) drawCrosshair(canvas, ins);
    }

    private void drawEmpty(Canvas canvas, Instr ins) {
        legendP.setColor(Theme.textFaint);
        String msg = ins.fmt(Double.NaN).equals("—") && !ticker.hasData()
                ? "该指标在本机不可读取 · 等待采样"
                : "等待采样…";
        canvas.drawText(msg, plot.left + Ui.dp(8), plot.centerY(), legendP);
    }

    private void drawGrid(Canvas canvas, Instr ins) {
        gridP.setColor(Theme.lineSoft);
        axisP.setColor(Theme.textFaint);
        int rows = 4;
        for (int i = 0; i <= rows; i++) {
            float y = plot.top + plot.height() * i / rows;
            canvas.drawLine(plot.left, y, plot.right, y, gridP);
        }
        // 竖网格
        float cols = 4;
        for (int i = 0; i <= cols; i++) {
            float x = plot.left + plot.width() * i / cols;
            canvas.drawLine(x, plot.top, x, plot.bottom, gridP);
        }
        for (int i = 0; i <= rows; i++) {
            float y = plot.top + plot.height() * i / rows;
            double price = visMax - (visMax - visMin) * i / rows;
            String s = ins.fmt(price);
            canvas.drawText(s, plot.right + Ui.dp(4), y + Ui.dp(3), axisP);
        }
    }

    private void drawWatermark(Canvas canvas) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Theme.alpha(Theme.text, 26));
        p.setTextSize(Ui.dp(26));
        p.setTypeface(Theme.monoBold);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("PERF TICKER", plot.centerX(), plot.centerY(), p);
    }

    private void drawCandles(Canvas canvas) {
        for (int i = 0; i < drawn.size(); i++) {
            Candle c = drawn.get(i);
            float cx = xAt(i);
            float yH = yOf(c.h);
            float yL = yOf(c.l);
            float yO = yOf(c.o);
            float yC = yOf(c.c);
            int col = c.bull() ? Theme.UP : Theme.DN;
            barP.setColor(col);
            wickP.setColor(Theme.alpha(col, 150));

            float lx = cx - 0.5f;
            canvas.drawRect(lx, yH, lx + 1f, yL + 0.7f, wickP);

            float bT = Math.min(yO, yC);
            float bB = Math.max(yO, yC);
            if (bB - bT < 1.2f) bB = bT + 1.2f;
            canvas.drawRect(cx - bodyW / 2f, bT, cx + bodyW / 2f, bB, barP);
        }
    }

    private float xAt(int i) {
        return firstX + stepX * i + stepX / 2f;
    }

    private float yOf(double price) {
        double r = (price - visMin) / (visMax - visMin);
        if (r < 0) r = 0;
        if (r > 1) r = 1;
        return (float) (plot.bottom - r * plot.height());
    }

    private void drawMAs(Canvas canvas, ArrayList<Candle> all, int start, Instr ins) {
        drawMa(canvas, all, start, 5, ma5P, 0xFFE8B84B);
        drawMa(canvas, all, start, 10, ma10P, 0xFF4A8DF8);
        drawMa(canvas, all, start, 20, ma20P, 0xFF35C6E8);
    }

    private void drawMa(Canvas canvas, ArrayList<Candle> all, int start, int n, Paint p, int color) {
        p.setColor(Theme.alpha(color, 210));
        boolean started = false;
        float prevX = 0;
        float prevY = 0;
        for (int i = 0; i < drawn.size(); i++) {
            double ma = maAt(all, start + i, n);
            if (Double.isNaN(ma)) {
                started = false;
                continue;
            }
            float cx = xAt(i);
            float y = yOf(ma);
            if (started) canvas.drawLine(prevX, prevY, cx, y, p);
            prevX = cx;
            prevY = y;
            started = true;
        }
    }

    private double maAt(ArrayList<Candle> all, int idx, int n) {
        if (idx < n - 1) return Double.NaN;
        double sum = 0;
        for (int i = idx - n + 1; i <= idx; i++) sum += all.get(i).c;
        return sum / n;
    }

    private void drawLegend(Canvas canvas, ArrayList<Candle> all, int start, Instr ins) {
        float x = plot.left;
        float y = plot.top - Ui.dp(6);
        double m5 = drawn.isEmpty() ? Double.NaN : maAt(all, start + drawn.size() - 1, 5);
        double m10 = drawn.isEmpty() ? Double.NaN : maAt(all, start + drawn.size() - 1, 10);
        double m20 = drawn.isEmpty() ? Double.NaN : maAt(all, start + drawn.size() - 1, 20);

        legendP.setColor(0xFFE8B84B);
        canvas.drawText("MA5 " + ins.fmt(m5), x, y, legendP);
        x += legendP.measureText("MA5 " + ins.fmt(m5)) + Ui.dp(10);
        legendP.setColor(0xFF4A8DF8);
        canvas.drawText("MA10 " + ins.fmt(m10), x, y, legendP);
        x += legendP.measureText("MA10 " + ins.fmt(m10)) + Ui.dp(10);
        legendP.setColor(0xFF35C6E8);
        canvas.drawText("MA20 " + ins.fmt(m20), x, y, legendP);
    }

    private void drawVolume(Canvas canvas, Instr ins) {
        legendP.setColor(Theme.textDim);
        Candle last = drawn.isEmpty() ? null : drawn.get(drawn.size() - 1);
        String v = last == null ? "—" : fmtVol(last.v);
        canvas.drawText("成交量 " + v, plot.left + Ui.dp(2), volRect.top - Ui.dp(4), legendP);

        gridP.setColor(Theme.lineSoft);
        canvas.drawLine(volRect.left, volRect.bottom, volRect.right, volRect.bottom, gridP);

        if (volMax <= 0) return;
        for (int i = 0; i < drawn.size(); i++) {
            Candle c = drawn.get(i);
            float cx = xAt(i);
            float hgt = (float) (c.v / volMax) * volRect.height();
            if (hgt < 1f) hgt = 1f;
            volP.setColor(Theme.alpha(c.bull() ? Theme.UP : Theme.DN, 150));
            canvas.drawRect(cx - bodyW / 2f, volRect.bottom - hgt, cx + bodyW / 2f, volRect.bottom, volP);
        }
    }

    private void drawTimeAxis(Canvas canvas) {
        if (drawn.isEmpty()) return;
        axisP.setColor(Theme.textFaint);
        axisP.setTextAlign(Paint.Align.LEFT);
        int n = drawn.size();
        float y = getHeight() - Ui.dp(2);
        float[] at = {0, n / 3f, n * 2f / 3f, n - 1};
        for (int i = 0; i < at.length; i++) {
            int idx = (int) at[i];
            if (idx < 0) idx = 0;
            if (idx >= n) idx = n - 1;
            String s = timeFmt.format(new Date(drawn.get(idx).ts));
            float tw = axisP.measureText(s);
            float x = xAt(idx) - tw / 2f;
            if (x < 0) x = 0;
            if (x + tw > plot.right) x = plot.right - tw;
            canvas.drawText(s, x, y, axisP);
        }
    }

    private void drawLastMark(Canvas canvas, Instr ins) {
        if (drawn.isEmpty()) return;
        Candle c = drawn.get(drawn.size() - 1);
        float y = yOf(c.c);
        int tone = Theme.tone(ticker.change());
        String s = ins.fmt(c.c);
        float tw = markP.measureText(s);
        float boxW = tw + Ui.dp(8);
        float bx = plot.right + Ui.dp(1);
        if (bx + boxW > getWidth()) bx = getWidth() - boxW;
        boxRect.set(bx, y - Ui.dp(7), bx + boxW, y + Ui.dp(7));
        boxBgP.setColor(tone);
        canvas.drawRoundRect(boxRect, Ui.dp(2), Ui.dp(2), boxBgP);
        markP.setColor(0xFFFFFFFF);
        canvas.drawText(s, bx + Ui.dp(4), y + Ui.dp(3), markP);
    }

    private void drawCrosshair(Canvas canvas, Instr ins) {
        Candle c = drawn.get(touchIdx);
        float cx = xAt(touchIdx);
        crossP.setColor(Theme.alpha(Theme.text, 150));
        canvas.drawLine(cx, plot.top, cx, plot.bottom, crossP);
        float cy = yOf(c.c);
        canvas.drawLine(plot.left, cy, plot.right, cy, crossP);

        float bw = Ui.dp(148);
        float bh = Ui.dp(64);
        float bx = plot.left + Ui.dp(4);
        float by = plot.top + Ui.dp(2);
        if (bx + bw > plot.right) bx = plot.right - bw - Ui.dp(2);
        boxRect.set(bx, by, bx + bw, by + bh);
        boxBgP.setColor(Theme.alpha(Theme.panel2, 235));
        canvas.drawRoundRect(boxRect, Ui.dp(3), Ui.dp(3), boxBgP);

        boxTextP.setColor(Theme.textDim);
        float tx = bx + Ui.dp(7);
        float ty = by + Ui.dp(14);
        canvas.drawText(timeFmt.format(new Date(c.ts)), tx, ty, boxTextP);

        boxTextP.setColor(Theme.text);
        float lh = Ui.dp(12);
        canvas.drawText("开 " + ins.fmt(c.o), tx, ty + lh, boxTextP);
        canvas.drawText("高 " + ins.fmt(c.h), tx + bw / 2f, ty + lh, boxTextP);
        canvas.drawText("低 " + ins.fmt(c.l), tx, ty + lh * 2, boxTextP);
        boxTextP.setColor(Theme.tone(c.c - c.o));
        canvas.drawText("收 " + ins.fmt(c.c), tx + bw / 2f, ty + lh * 2, boxTextP);
        boxTextP.setColor(Theme.textDim);
        canvas.drawText("量 " + fmtVol(c.v), tx, ty + lh * 3, boxTextP);
    }

    private String fmtVol(double v) {
        if (Double.isNaN(v)) return "—";
        if (v >= 1000000) return String.format(Locale.US, "%.1fM", v / 1000000.0);
        if (v >= 1000) return String.format(Locale.US, "%.1fK", v / 1000.0);
        return String.format(Locale.US, "%.0f", v);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (!drawn.isEmpty() && stepX > 0) {
                int idx = (int) Math.floor((e.getX() - firstX) / stepX);
                if (idx < 0) idx = 0;
                if (idx >= drawn.size()) idx = drawn.size() - 1;
                touchIdx = idx;
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchIdx = -1;
            invalidate();
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
