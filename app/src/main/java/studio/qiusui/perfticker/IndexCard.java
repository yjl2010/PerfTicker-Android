package studio.qiusui.perfticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

/** 顶部指数格：名称 / 现价 / 涨跌 / 迷你 K 线，点一下切换品种。 */
final class IndexCard extends View {

    private Ticker ticker;
    private boolean selected;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint();
    private final Paint wickPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final RectF rect = new RectF();

    IndexCard(Context c) {
        super(c);
        init();
    }

    IndexCard(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Ui.dp(0.8f));
        namePaint.setTextSize(Ui.dp(10.5f));
        valuePaint.setTextSize(Ui.dp(16f));
        valuePaint.setTypeface(Theme.monoBold);
        chgPaint.setTextSize(Ui.dp(9.5f));
        chgPaint.setTypeface(Theme.mono);
        bodyPaint.setStyle(Paint.Style.FILL);
        wickPaint.setStyle(Paint.Style.FILL);
        gridPaint.setStyle(Paint.Style.FILL);
        setClickable(true);
        setBackground(null);
    }

    void setTicker(Ticker t) {
        ticker = t;
    }

    void setSelected(boolean s) {
        selected = s;
        invalidate();
    }

    String code() {
        return ticker == null ? "" : ticker.instr.code;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        bgPaint.setColor(Theme.panel);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, Ui.dp(4), Ui.dp(4), bgPaint);
        borderPaint.setColor(selected ? Theme.accent : Theme.line);
        canvas.drawRoundRect(rect, Ui.dp(4), Ui.dp(4), borderPaint);

        namePaint.setColor(Theme.textDim);
        canvas.drawText("", 0, 0, namePaint);

        if (ticker == null) return;
        Instr ins = ticker.instr;
        double chg = ticker.change();
        int tone = Theme.tone(chg);

        float x = Ui.dp(7);
        float y = Ui.dp(14);

        namePaint.setColor(Theme.textDim);
        namePaint.setTextSize(Ui.dp(10.5f));
        canvas.drawText(trimText(ins.name, namePaint, w - Ui.dp(14)), x, y, namePaint);

        valuePaint.setColor(Theme.text);
        valuePaint.setTextSize(Ui.dp(16f));
        String v = ins.fmt(ticker.last);
        canvas.drawText(v, x, y + Ui.dp(20), valuePaint);

        float vw = valuePaint.measureText(v);
        Paint unitPaint = chgPaint;
        unitPaint.setColor(Theme.textFaint);
        canvas.drawText(ins.unit, x + vw + Ui.dp(2), y + Ui.dp(20), unitPaint);

        chgPaint.setColor(tone);
        String cs = ins.fmtSigned(chg);
        canvas.drawText(cs, x, y + Ui.dp(33), chgPaint);

        // 迷你 K 线走底部条带
        float sy = h - Ui.dp(22);
        float sh = Ui.dp(16);
        int idx = 1;
        ArrayList<Candle> bars = ticker.bars(idx, 22);
        if (bars.size() >= 2) {
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
            float step = (w - Ui.dp(14)) / (float) bars.size();
            for (int i = 0; i < bars.size(); i++) {
                Candle c = bars.get(i);
                float cx = x + step * i + step / 2f;
                float yH = sy + sh - (float) ((c.h - min) / (max - min)) * sh;
                float yL = sy + sh - (float) ((c.l - min) / (max - min)) * sh;
                float yO = sy + sh - (float) ((c.o - min) / (max - min)) * sh;
                float yC = sy + sh - (float) ((c.c - min) / (max - min)) * sh;
                int col = c.bull() ? Theme.UP : Theme.DN;
                bodyPaint.setColor(col);
                wickPaint.setColor(Theme.alpha(col, 170));
                float bw = Math.max(1.2f, step * 0.6f);
                canvas.drawRect(cx - 0.5f, yH, cx + 0.5f, yL + 0.5f, wickPaint);
                float bT = Math.min(yO, yC);
                float bB = Math.max(yO, yC);
                if (bB - bT < 1f) bB = bT + 1f;
                rect.set(cx - bw / 2f, bT, cx + bw / 2f, bB);
                canvas.drawRect(rect, bodyPaint);
            }
        }
    }

    private String trimText(String s, Paint p, float maxW) {
        if (p.measureText(s) <= maxW) return s;
        for (int i = s.length() - 1; i > 0; i--) {
            String t = s.substring(0, i) + "…";
            if (p.measureText(t) <= maxW) return t;
        }
        return "…";
    }
}
