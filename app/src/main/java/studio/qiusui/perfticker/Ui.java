package studio.qiusui.perfticker;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 轻量 UI 工具：dp、文本、行/列容器、分割线、筹码胶囊。全自绘风格的排版底座。 */
final class Ui {

    static float density = 2f;

    private Ui() {
    }

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold, boolean mono) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        if (mono) {
            v.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        } else if (bold) {
            v.setTypeface(Typeface.DEFAULT_BOLD);
        }
        v.setIncludeFontPadding(false);
        return v;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static View hline(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(0.7f)));
        return v;
    }

    static void pad(View v, float l, float t, float r, float b) {
        v.setPadding(dp(l), dp(t), dp(r), dp(b));
    }

    static void bg(View v, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            g.setStroke(dp(0.7f), stroke);
        }
        v.setBackground(g);
    }

    static GradientDrawable bgDrawable(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(0.7f), stroke);
        return g;
    }

    static TextView chip(Context c, String label, float sp) {
        TextView v = text(c, label, sp, Theme.textDim, false, false);
        v.setGravity(android.view.Gravity.CENTER);
        v.setPadding(dp(9), dp(4), dp(9), dp(4));
        return v;
    }

    static void setChipState(TextView v, boolean on) {
        v.setTextColor(on ? 0xFFFFFFFF : Theme.textDim);
        v.setBackground(bgDrawable(on ? Theme.accent : 0x00000000, on ? 0 : Theme.line, 4));
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lpw(float weight, int h) {
        return new LinearLayout.LayoutParams(0, h, weight);
    }

    static int widthOf(View v) {
        int w = v.getWidth();
        return w;
    }
}
