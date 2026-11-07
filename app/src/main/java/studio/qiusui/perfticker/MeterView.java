package studio.qiusui.perfticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 营收中心里的负载条：底槽 + 填充 + 盈亏平衡竖线。 */
final class MeterView extends View {

    private double pct = Double.NaN;
    private double be = 30;
    private boolean profit;

    private final Paint trackP = new Paint();
    private final Paint fillP = new Paint();
    private final Paint beP = new Paint();
    private final RectF r = new RectF();

    MeterView(Context c) {
        super(c);
        trackP.setStyle(Paint.Style.FILL);
        fillP.setStyle(Paint.Style.FILL);
        beP.setStyle(Paint.Style.FILL);
    }

    void set(double pct, double be, boolean profit) {
        this.pct = pct;
        this.be = be;
        this.profit = profit;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        trackP.setColor(Theme.alpha(Theme.line, 200));
        r.set(0, 0, w, h);
        canvas.drawRoundRect(r, h / 2f, h / 2f, trackP);

        double p = Double.isNaN(pct) ? 0 : Math.max(0, Math.min(100, pct));
        fillP.setColor(Theme.alpha(profit ? Theme.UP : Theme.DN, 235));
        if (p > 0) {
            r.set(0, 0, (float) (w * p / 100.0), h);
            canvas.drawRoundRect(r, h / 2f, h / 2f, fillP);
        }

        beP.setColor(Theme.alpha(Theme.gold, 230));
        float bx = (float) (w * Math.max(0, Math.min(100, be)) / 100.0);
        canvas.drawRect(bx - 1, -Ui.dp(2), bx + 1, h + Ui.dp(2), beP);
    }
}
