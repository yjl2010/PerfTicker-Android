package studio.qiusui.perfticker;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * 深色证券终端视觉系统：配色 / 字号阶梯 / 画笔工厂。
 * 对应原工程 Theme.cs，一个 App 内只保留两套配色（深色 / 浅色）。
 */
final class Theme {

    static boolean dark = true;
    /** true = A 股习惯 红涨绿跌；false = 国际配色 绿涨红跌 */
    static boolean redUp = true;

    static int bg;
    static int panel;
    static int panel2;
    static int line;
    static int lineSoft;
    static int text;
    static int textDim;
    static int textFaint;
    static int accent;
    static int gold;
    static int UP;
    static int DN;
    static int flat;

    static Typeface mono;
    static Typeface monoBold;
    static Typeface sans;
    static Typeface sansBold;

    private Theme() {
    }

    static void apply(boolean isDark, boolean isRedUp) {
        dark = isDark;
        redUp = isRedUp;
        if (dark) {
            bg = 0xFF070B12;
            panel = 0xFF0D1420;
            panel2 = 0xFF121B29;
            line = 0xFF1D2838;
            lineSoft = 0xFF141E2C;
            text = 0xFFE8EFF8;
            textDim = 0xFF8494A8;
            textFaint = 0xFF4F5E72;
            accent = 0xFF2E6BE6;
            gold = 0xFFE8B84B;
            flat = 0xFF8A97A6;
        } else {
            bg = 0xFFF3F5F9;
            panel = 0xFFFFFFFF;
            panel2 = 0xFFF7F9FC;
            line = 0xFFDFE5EE;
            lineSoft = 0xFFEDF1F6;
            text = 0xFF14202E;
            textDim = 0xFF6A7889;
            textFaint = 0xFFA7B2C0;
            accent = 0xFF2358C8;
            gold = 0xFFB4841F;
            flat = 0xFF7A8794;
        }
        UP = redUp ? 0xFFEF3F4A : 0xFF16A96B;
        DN = redUp ? 0xFF16A96B : 0xFFEF3F4A;
        mono = Typeface.MONOSPACE;
        monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        sans = Typeface.DEFAULT;
        sansBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    }

    static void init() {
        apply(true, true);
    }

    /** 涨跌颜色：v>0 涨，v<0 跌 */
    static int tone(double v) {
        if (Double.isNaN(v) || Math.abs(v) < 1e-9) return flat;
        return v > 0 ? UP : DN;
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    static Paint stroke(int color, float w) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        return p;
    }

    static Paint text(int color, float sp, Typeface tf) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sp);
        p.setTypeface(tf);
        return p;
    }

    static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
