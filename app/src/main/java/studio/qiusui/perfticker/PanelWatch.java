package studio.qiusui.perfticker;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** 自选行情：按板块分组的全部品种，带迷你 K 线与现价 / 涨跌。 */
final class PanelWatch {

    final LinearLayout root;
    private final ArrayList<Row> rows = new ArrayList<Row>();
    private String selected;

    private static final class Row {
        Ticker ticker;
        TextView price;
        TextView chg;
        SparkView spark;
        View item;
    }

    PanelWatch(Context c, ArrayList<Ticker> tickers, OnPick cb) {
        root = Ui.col(c);
        Ui.bg(root, Theme.panel, Theme.line, 5);

        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(10), Ui.dp(7), Ui.dp(10), Ui.dp(6));
        TextView t = Ui.text(c, "自选行情", 13, Theme.text, true, false);
        TextView en = Ui.text(c, "WATCHLIST", 8.5f, Theme.textFaint, false, true);
        en.setPadding(Ui.dp(6), Ui.dp(2), 0, 0);
        TextView count = Ui.text(c, tickers.size() + " 只", 9.5f, Theme.textDim, false, true);
        head.addView(t, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(en, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1, 1);
        head.addView(new View(c), sp);
        head.addView(count, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(head, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.hline(c, Theme.line));

        LinearLayout colHead = Ui.row(c);
        colHead.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        addFixed(c, colHead, "代码", 46);
        addWeighted(c, colHead, "名称");
        addFixed(c, colHead, "走势", 56);
        addRight(c, colHead, "现价", 54);
        addRight(c, colHead, "涨跌", 52);
        root.addView(colHead, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.hline(c, Theme.lineSoft));

        String lastGroup = null;
        for (int i = 0; i < tickers.size(); i++) {
            Ticker tk = tickers.get(i);
            String g = tk.instr.group;
            if (!g.equals(lastGroup)) {
                lastGroup = g;
                root.addView(groupHeader(c, g), Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            Row r = buildRow(c, tk, cb);
            rows.add(r);
            root.addView(r.item, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(25)));
        }
    }

    private View groupHeader(Context c, String name) {
        LinearLayout l = Ui.row(c);
        l.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(3));
        View bar = new View(c);
        bar.setBackgroundColor(Theme.alpha(Theme.accent, 200));
        l.addView(bar, Ui.lp(Ui.dp(2), Ui.dp(11)));
        TextView t = Ui.text(c, name, 10.5f, Theme.textDim, true, false);
        t.setPadding(Ui.dp(5), 0, 0, 0);
        l.addView(t, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private Row buildRow(Context c, final Ticker tk, final OnPick cb) {
        Row r = new Row();
        r.ticker = tk;
        LinearLayout item = Ui.row(c);
        item.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);

        TextView code = Ui.text(c, tk.instr.code, 9.5f, Theme.textFaint, false, true);
        code.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(46), LinearLayout.LayoutParams.WRAP_CONTENT));
        item.addView(code);

        TextView name = Ui.text(c, tk.instr.name, 11.5f, Theme.text, false, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        item.addView(name);

        SparkView spark = new SparkView(c);
        spark.setTicker(tk);
        spark.setPeriod(1);
        item.addView(spark, new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(17)));

        TextView price = Ui.text(c, "—", 12, Theme.text, true, true);
        price.setGravity(android.view.Gravity.RIGHT);
        price.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(54), LinearLayout.LayoutParams.WRAP_CONTENT));
        item.addView(price);

        TextView chg = Ui.text(c, "—", 10.5f, Theme.textDim, false, true);
        chg.setGravity(android.view.Gravity.RIGHT);
        chg.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(52), LinearLayout.LayoutParams.WRAP_CONTENT));
        item.addView(chg);

        r.price = price;
        r.chg = chg;
        r.spark = spark;
        r.item = item;

        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cb != null) cb.onPick(tk.instr.code);
            }
        });
        return r;
    }

    private void addFixed(Context c, LinearLayout parent, String text, int wdp) {
        TextView v = Ui.text(c, text, 9, Theme.textFaint, false, false);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(wdp), LinearLayout.LayoutParams.WRAP_CONTENT));
        parent.addView(v);
    }

    private void addWeighted(Context c, LinearLayout parent, String text) {
        TextView v = Ui.text(c, text, 9, Theme.textFaint, false, false);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(v);
    }

    private void addRight(Context c, LinearLayout parent, String text, int wdp) {
        TextView v = Ui.text(c, text, 9, Theme.textFaint, false, false);
        v.setGravity(android.view.Gravity.RIGHT);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(wdp), LinearLayout.LayoutParams.WRAP_CONTENT));
        parent.addView(v);
    }

    void setSelected(String code) {
        selected = code;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean on = r.ticker.instr.code.equals(code);
            r.item.setBackground(on ? Ui.bgDrawable(Theme.alpha(Theme.accent, 26), 0, 0) : null);
        }
    }

    void refresh() {
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            double ch = r.ticker.change();
            r.price.setText(r.ticker.instr.fmt(r.ticker.last));
            r.price.setTextColor(Theme.tone(ch));
            r.chg.setText(r.ticker.instr.fmtSigned(ch));
            r.chg.setTextColor(Theme.tone(ch));
            r.spark.invalidate();
        }
    }
}
