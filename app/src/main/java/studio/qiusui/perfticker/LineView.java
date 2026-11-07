package studio.qiusui.perfticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;

/** 净收走势迷你折线（零轴居中，红上绿下）。 */
final class LineView extends View {

    private final ArrayList<Double> values = new ArrayList<Double>();
    private final Paint lineP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeroP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);

    LineView(Context c) {
        super(c);
        lineP.setStyle(Paint.Style.STROKE);
        lineP.setStrokeWidth(Ui.dp(1.4f));
        zeroP.setStyle(Paint.Style.STROKE);
        zeroP.setStrokeWidth(Ui.dp(0.7f));
        fillP.setStyle(Paint.Style.FILL);
    }

    void setValues(ArrayList<Double> src) {
        values.clear();
        values.addAll(src);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        double min = 0;
        double max = 0;
        for (int i = 0; i < values.size(); i++) {
            double v = values.get(i);
            if (Double.isNaN(v)) continue;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (max - min < 1e-9) {
            max = 1;
            min = -1;
        }
        min -= (max - min) * 0.1;
        max += (max - min) * 0.1;

        float top = Ui.dp(3);
        float bottom = h - Ui.dp(3);
        float zeroY = (float) (bottom - (0 - min) / (max - min) * (bottom - top));

        zeroP.setColor(Theme.alpha(Theme.line, 220));
        canvas.drawLine(0, zeroY, w, zeroY, zeroP);

        if (values.size() < 2) return;
        float step = w / (float) (values.size() - 1);
        lineP.setColor(Theme.alpha(Theme.gold, 220));
        float prevX = 0;
        float prevY = 0;
        for (int i = 0; i < values.size(); i++) {
            double v = values.get(i);
            if (Double.isNaN(v)) continue;
            float x = step * i;
            float y = (float) (bottom - (v - min) / (max - min) * (bottom - top));
            if (i > 0 && !Double.isNaN(values.get(i - 1))) canvas.drawLine(prevX, prevY, x, y, lineP);
            prevX = x;
            prevY = y;
        }
    }
}
