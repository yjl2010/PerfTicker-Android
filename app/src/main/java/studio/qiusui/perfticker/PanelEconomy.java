package studio.qiusui.perfticker;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** 营收中心：累计营收 / 每秒净收 / 峰值 / 五个经营单元负载条与盈亏标签 / 净收走势。 */
final class PanelEconomy {

    final LinearLayout root;
    private final TextView total;
    private final TextView perSec;
    private final TextView peak;
    private final TextView dur;
    private final TextView state;
    private final TextView[] values = new TextView[5];
    private final TextView[] tags = new TextView[5];
    private final MeterView[] meters = new MeterView[5];
    private final LineView line;
    private final TextView weight;

    PanelEconomy(Context c, final Runnable onReset) {
        root = Ui.col(c);
        Ui.bg(root, Theme.panel, Theme.line, 5);

        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(10), Ui.dp(7), Ui.dp(10), Ui.dp(4));
        TextView t = Ui.text(c, "营收中心", 13, Theme.text, true, false);
        TextView en = Ui.text(c, "REVENUE", 8.5f, Theme.textFaint, false, true);
        en.setPadding(Ui.dp(6), Ui.dp(2), 0, 0);
        state = Ui.text(c, "已暂停", 9.5f, Theme.textFaint, false, false);
        state.setPadding(Ui.dp(6), 0, 0, 0);
        TextView reset = Ui.chip(c, "归零", 10f);
        reset.setTextColor(Theme.textDim);
        reset.setBackground(Ui.bgDrawable(0, Theme.line, 4));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onReset != null) onReset.run();
            }
        });
        head.addView(t, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(en, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(state, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        head.addView(reset, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(head, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.hline(c, Theme.line));

        LinearLayout money = Ui.row(c);
        money.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(2));
        TextView yen = Ui.text(c, "¥", 15, Theme.gold, true, false);
        total = Ui.text(c, "+0.00", 27, Theme.gold, true, true);
        LinearLayout right = Ui.col(c);
        right.setGravity(android.view.Gravity.RIGHT);
        perSec = Ui.text(c, "0.00 元/秒", 11, Theme.textDim, false, true);
        peak = Ui.text(c, "峰值 ¥0.00", 9.5f, Theme.textFaint, false, true);
        dur = Ui.text(c, "本时段 00:00", 9.5f, Theme.textFaint, false, true);
        right.addView(perSec, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        right.addView(peak, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        right.addView(dur, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        money.addView(yen, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        money.addView(total, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        money.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        money.addView(right, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(money, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.hline(c, Theme.lineSoft));

        for (int i = 0; i < 5; i++) {
            LinearLayout block = Ui.col(c);
            block.setPadding(Ui.dp(10), Ui.dp(5), Ui.dp(10), Ui.dp(3));

            LinearLayout row = Ui.row(c);
            TextView name = Ui.text(c, Economy.UNITS[i] + "  " + String.format(Locale.US, "%.1f", Economy.CAP[i]) + " 元/s",
                    9.5f, Theme.textDim, false, false);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(name);

            TextView tag = Ui.text(c, "—", 9f, Theme.textFaint, false, false);
            tag.setPadding(0, 0, Ui.dp(8), 0);
            row.addView(tag, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView value = Ui.text(c, "—", 11.5f, Theme.text, true, true);
            value.setGravity(android.view.Gravity.RIGHT);
            value.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(74), LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(value);

            block.addView(row, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            MeterView meter = new MeterView(c);
            block.addView(meter, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(6)));

            values[i] = value;
            tags[i] = tag;
            meters[i] = meter;
            root.addView(block, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        root.addView(Ui.hline(c, Theme.lineSoft));
        LinearLayout chartBox = Ui.col(c);
        chartBox.setPadding(Ui.dp(10), Ui.dp(5), Ui.dp(10), Ui.dp(4));
        TextView label = Ui.text(c, "净收走势 · 元/秒", 9.5f, Theme.textFaint, false, false);
        chartBox.addView(label, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        line = new LineView(c);
        chartBox.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(38)));
        root.addView(chartBox, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        weight = Ui.text(c, "", 8.5f, Theme.textFaint, false, false);
        weight.setPadding(Ui.dp(10), Ui.dp(3), Ui.dp(10), Ui.dp(6));
        root.addView(weight, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    void refresh(Economy eco) {
        String sign = eco.total >= 0 ? "+" : "-";
        total.setText(sign + String.format(Locale.US, "%.2f", Math.abs(eco.total)));
        total.setTextColor(eco.total >= 0 ? Theme.gold : Theme.DN);
        perSec.setText(String.format(Locale.US, "%+.2f 元/秒", eco.netPerSec));
        perSec.setTextColor(Theme.tone(eco.netPerSec));
        peak.setText("峰值 ¥" + String.format(Locale.US, "%.2f", eco.peak));
        long sec = eco.seconds;
        dur.setText(String.format(Locale.US, "本时段 %02d:%02d", sec / 60, sec % 60));
        state.setText(eco.enabled ? "结算中" : "已暂停");
        state.setTextColor(eco.enabled ? Theme.UP : Theme.textFaint);

        for (int i = 0; i < 5; i++) {
            double v = eco.net[i];
            boolean profit = !Double.isNaN(v) && v > 0;
            values[i].setText(Double.isNaN(v) ? "—" : String.format(Locale.US, "%+.3f", v));
            values[i].setTextColor(Double.isNaN(v) ? Theme.textFaint : Theme.tone(v));
            tags[i].setText(Double.isNaN(eco.load[i]) ? "无数据" : profit ? "盈利" : "亏损");
            tags[i].setTextColor(Double.isNaN(eco.load[i]) ? Theme.textFaint : profit ? Theme.UP : Theme.DN);
            meters[i].set(eco.load[i], Economy.BREAK_EVEN[i], profit);
        }
        line.setValues(eco.historyList());
        weight.setText("营收基准折算系数（按本机核数 / 内存 / 容量自适应）：" + eco.weightText());
    }
}
