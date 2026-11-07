package studio.qiusui.perfticker;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** 品种头：现价 / 涨跌 / 今开 / 最高 / 最低 / 均价 / 振幅 / 成交量。 */
final class PanelHeader {

    final LinearLayout root;
    private final TextView name;
    private final TextView code;
    private final TextView price;
    private final TextView unit;
    private final TextView chg;
    private final TextView chgPct;
    private final TextView vOpen;
    private final TextView vHigh;
    private final TextView vLow;
    private final TextView vAvg;
    private final TextView vAmp;
    private final TextView vVol;

    private Ticker ticker;

    PanelHeader(Context c) {
        root = Ui.col(c);
        root.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(6));
        Ui.bg(root, Theme.panel, Theme.line, 5);

        LinearLayout top = Ui.row(c);
        name = Ui.text(c, "—", 15, Theme.text, true, false);
        code = Ui.text(c, "—", 10, Theme.textDim, false, true);
        chgPct = Ui.text(c, "—", 12, Theme.textDim, false, true);
        top.addView(name, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        code.setPadding(Ui.dp(6), 0, 0, 0);
        top.addView(code, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1, 1);
        top.addView(new View(c), sp);
        top.addView(chgPct, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(top, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout line2 = Ui.row(c);
        line2.setPadding(0, Ui.dp(2), 0, 0);
        price = Ui.text(c, "—", 30, Theme.text, true, true);
        unit = Ui.text(c, "", 11, Theme.textFaint, false, false);
        chg = Ui.text(c, "—", 13, Theme.textDim, true, true);
        line2.addView(price, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        unit.setPadding(Ui.dp(3), Ui.dp(10), 0, 0);
        line2.addView(unit, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0, 1, 1);
        line2.addView(new View(c), sp2);
        line2.addView(chg, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(line2, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout grid1 = Ui.row(c);
        grid1.setPadding(0, Ui.dp(6), 0, 0);
        vOpen = cell(c, grid1, "今开");
        vHigh = cell(c, grid1, "最高");
        vLow = cell(c, grid1, "最低");
        root.addView(grid1, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout grid2 = Ui.row(c);
        grid2.setPadding(0, Ui.dp(3), 0, 0);
        vAvg = cell(c, grid2, "均价");
        vAmp = cell(c, grid2, "振幅");
        vVol = cell(c, grid2, "成交量");
        root.addView(grid2, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private TextView cell(Context c, LinearLayout parent, String label) {
        LinearLayout box = Ui.row(c);
        box.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView l = Ui.text(c, label, 9.5f, Theme.textFaint, false, false);
        TextView v = Ui.text(c, "—", 11, Theme.textDim, false, true);
        box.addView(l, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        v.setPadding(Ui.dp(4), 0, 0, 0);
        box.addView(v, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        parent.addView(box);
        return v;
    }

    void setTicker(Ticker t) {
        ticker = t;
        refresh();
    }

    void refresh() {
        if (ticker == null) return;
        Instr ins = ticker.instr;
        name.setText(ins.name);
        code.setText(ins.code + "  " + ins.group);
        unit.setText(ins.unit);

        double last = ticker.last;
        double ch = ticker.change();
        double pct = ticker.changePct();
        price.setText(ins.fmt(last));
        price.setTextColor(Theme.tone(ch));
        chg.setText(ins.fmtSigned(ch) + ins.unit);
        chg.setTextColor(Theme.tone(ch));
        chgPct.setText(Double.isNaN(pct) ? "—" : String.format(Locale.US, "%+.2f%%", pct));
        chgPct.setTextColor(Theme.tone(ch));

        vOpen.setText(ins.fmt(ticker.open));
        vHigh.setText(ins.fmt(ticker.high));
        vLow.setText(ins.fmt(ticker.low));
        vAvg.setText(ins.fmt(ticker.avg()));
        double amp = ticker.amplitude();
        vAmp.setText(Double.isNaN(amp) ? "—" : String.format(Locale.US, "%.1f%%", amp));
        vVol.setText(fmtVol(ticker.volume));
    }

    static String fmtVol(double v) {
        if (Double.isNaN(v)) return "—";
        if (v >= 1000000) return String.format(Locale.US, "%.1fM", v / 1000000.0);
        if (v >= 1000) return String.format(Locale.US, "%.1fK", v / 1000.0);
        return String.format(Locale.US, "%.0f", v);
    }
}
