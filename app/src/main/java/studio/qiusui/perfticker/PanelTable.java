package studio.qiusui.perfticker;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * 个股列表：全品种行情表（代码 / 名称 / 现价 / 涨跌 / 走势 / 成交量），支持排序。
 * Android 不允许枚举其它 App 的进程（/proc 受 hidepid 限制），因此原工程的进程表改为全品种表，
 * 数值全部是真实采集结果，不做臆造。
 */
final class PanelTable {

    static final int SORT_CODE = 0;
    static final int SORT_PRICE = 1;
    static final int SORT_CHG = 2;
    static final int SORT_VOL = 3;

    final LinearLayout root;
    private final LinearLayout body;
    private final ArrayList<Row> rows = new ArrayList<Row>();
    private int sortMode = SORT_CODE;
    private boolean asc = true;
    private String selected;

    private static final class Row {
        Ticker ticker;
        TextView price;
        TextView chg;
        TextView vol;
        SparkView spark;
        View item;
    }

    PanelTable(Context c, ArrayList<Ticker> tickers, OnPick cb) {
        root = Ui.col(c);
        Ui.bg(root, Theme.panel, Theme.line, 5);

        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(10), Ui.dp(7), Ui.dp(10), Ui.dp(5));
        TextView t = Ui.text(c, "个股列表", 13, Theme.text, true, false);
        TextView en = Ui.text(c, "ALL INSTRUMENTS", 8.5f, Theme.textFaint, false, true);
        en.setPadding(Ui.dp(6), Ui.dp(2), 0, 0);
        TextView count = Ui.text(c, tickers.size() + " 只", 9.5f, Theme.textDim, false, true);
        head.addView(t, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(en, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        head.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        head.addView(count, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(head, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.hline(c, Theme.line));

        LinearLayout colHead = Ui.row(c);
        colHead.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        fix(c, colHead, "代码", 44, false);
        weight(c, colHead, "名称");
        right(c, colHead, "现价", 56);
        right(c, colHead, "涨跌", 52);
        fix(c, colHead, "走势", 50, false);
        right(c, colHead, "量", 52);
        root.addView(colHead, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.hline(c, Theme.lineSoft));

        body = Ui.col(c);
        root.addView(body, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < tickers.size(); i++) {
            rows.add(buildRow(c, tickers.get(i), cb));
        }
        applyOrder();
    }

    private Row buildRow(Context c, final Ticker tk, final OnPick cb) {
        Row r = new Row();
        r.ticker = tk;
        LinearLayout item = Ui.row(c);
        item.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
        item.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(26)));

        TextView code = Ui.text(c, tk.instr.code, 9.5f, Theme.textFaint, false, true);
        code.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(44), LinearLayout.LayoutParams.WRAP_CONTENT));
        item.addView(code);

        TextView name = Ui.text(c, tk.instr.name, 11, Theme.text, false, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        item.addView(name);

        TextView price = Ui.text(c, "—", 11.5f, Theme.text, true, true);
        price.setGravity(android.view.Gravity.RIGHT);
        price.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(56), LinearLayout.LayoutParams.WRAP_CONTENT));
        item.addView(price);

        TextView chg = Ui.text(c, "—", 10, Theme.textDim, false, true);
        chg.setGravity(android.view.Gravity.RIGHT);
        chg.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(52), LinearLayout.LayoutParams.WRAP_CONTENT));
        item.addView(chg);

        SparkView spark = new SparkView(c);
        spark.setTicker(tk);
        spark.setPeriod(1);
        item.addView(spark, new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(16)));

        TextView vol = Ui.text(c, "—", 9.5f, Theme.textDim, false, true);
        vol.setGravity(android.view.Gravity.RIGHT);
        vol.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(52), LinearLayout.LayoutParams.WRAP_CONTENT));
        item.addView(vol);

        r.price = price;
        r.chg = chg;
        r.vol = vol;
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

    private void fix(Context c, LinearLayout parent, String s, int wdp, boolean right) {
        TextView v = Ui.text(c, s, 9, Theme.textFaint, false, false);
        if (right) v.setGravity(android.view.Gravity.RIGHT);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(wdp), LinearLayout.LayoutParams.WRAP_CONTENT));
        parent.addView(v);
    }

    private void weight(Context c, LinearLayout parent, String s) {
        TextView v = Ui.text(c, s, 9, Theme.textFaint, false, false);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        parent.addView(v);
    }

    private void right(Context c, LinearLayout parent, String s, int wdp) {
        fix(c, parent, s, wdp, true);
    }

    void setSort(int mode) {
        if (sortMode == mode) {
            asc = !asc;
        } else {
            sortMode = mode;
            asc = mode == SORT_CODE;
        }
        applyOrder();
    }

    void sort(int mode, boolean ascending) {
        sortMode = mode;
        asc = ascending;
        applyOrder();
    }

    int sortMode() {
        return sortMode;
    }

    private void applyOrder() {
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                int r;
                if (sortMode == SORT_PRICE) {
                    r = Double.compare(a.ticker.last, b.ticker.last);
                } else if (sortMode == SORT_CHG) {
                    r = Double.compare(a.ticker.changePct(), b.ticker.changePct());
                } else if (sortMode == SORT_VOL) {
                    r = Double.compare(a.ticker.volume, b.ticker.volume);
                } else {
                    r = a.ticker.instr.code.compareTo(b.ticker.instr.code);
                }
                return asc ? r : -r;
            }
        });
        body.removeAllViews();
        for (int i = 0; i < rows.size(); i++) {
            body.addView(rows.get(i).item);
            body.addView(Ui.hline(body.getContext(), Theme.lineSoft));
        }
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
            r.vol.setText(PanelHeader.fmtVol(r.ticker.volume));
            r.spark.invalidate();
        }
    }
}
