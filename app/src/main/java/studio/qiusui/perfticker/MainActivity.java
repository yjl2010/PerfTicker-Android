package studio.qiusui.perfticker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * 主界面：标题栏 / 页签 / 行情 · 逐笔 · 全表 · 营收 四个页面 / 底部跑马灯状态条。
 * 页面结构对应原工程 AppForm.cs，按手机竖屏做了单列重排。
 */
public class MainActivity extends Activity {

    private Economy eco;
    private Sampler sampler;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private boolean refreshPending;

    private String selectedCode;
    private int periodIdx;
    private int tabIdx;

    private LinearLayout root;
    private final LinearLayout[] pages = new LinearLayout[4];
    private final TextView[] tabChips = new TextView[4];
    private final ArrayList<IndexCard> indexCards = new ArrayList<IndexCard>();
    private final TextView[] periodChips = new TextView[Ticker.PERIOD_NAMES.length];
    private final TextView[] sortChips = new TextView[4];

    private PanelHeader header;
    private ChartView chart;
    private PanelWatch watch;
    private PanelTable table;
    private PanelTape tape;
    private PanelEconomy ecoPanel;
    private Marquee marquee;

    private TextView topClock;
    private TextView topStatus;
    private TextView statusLeft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Store.init(this);
        Theme.init();
        Theme.apply(Store.theme() == 0, Store.redUp());
        Ui.init(this);
        Probe.init(this);

        selectedCode = Store.selected();
        periodIdx = Store.period();
        tabIdx = Store.tab();
        if (periodIdx < 0 || periodIdx >= Ticker.PERIOD_NAMES.length) periodIdx = 1;
        if (tabIdx < 0 || tabIdx > 3) tabIdx = 0;

        eco = new Economy();
        eco.total = Store.ecoTotal();
        eco.peak = Store.ecoPeak();
        eco.seconds = Store.ecoSeconds();
        eco.enabled = Store.economyOn();

        sampler = new Sampler(eco, Store.intervalMs());

        buildUi();
        applyTab(tabIdx);
        select(selectedCode, false);
        applyPeriod(periodIdx);

        sampler.setListener(new Sampler.Listener() {
            @Override
            public void onSample(Snap s) {
                postRefresh();
            }

            @Override
            public void onTrade(Trade t) {
                postRefresh();
            }
        });
        sampler.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sampler != null && !sampler.running) sampler.start();
        postRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistEconomy();
        if (sampler != null) sampler.stop();
    }

    @Override
    protected void onDestroy() {
        persistEconomy();
        if (sampler != null) sampler.stop();
        super.onDestroy();
    }

    private void persistEconomy() {
        if (eco == null) return;
        Store.ecoTotal(eco.total);
        Store.ecoPeak(eco.peak);
        Store.ecoSeconds(eco.seconds);
    }

    // ---------------------------------------------------------------- 布局

    private void buildUi() {
        getWindow().setStatusBarColor(Theme.bg);
        getWindow().setNavigationBarColor(Theme.bg);

        root = Ui.col(this);
        root.setBackgroundColor(Theme.bg);

        // 标题栏
        LinearLayout top = Ui.row(this);
        top.setPadding(Ui.dp(10), Ui.dp(7), Ui.dp(10), Ui.dp(6));
        View accent = new View(this);
        accent.setBackgroundColor(Theme.accent);
        top.addView(accent, Ui.lp(Ui.dp(3), Ui.dp(26)));

        LinearLayout brand = Ui.col(this);
        brand.setPadding(Ui.dp(7), 0, 0, 0);
        TextView name = Ui.text(this, "性能行情终端", 14.5f, Theme.text, true, false);
        TextView sub = Ui.text(this, "PERF TERMINAL · ANDROID", 7.5f, Theme.textFaint, false, true);
        brand.addView(name, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        brand.addView(sub, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        top.addView(brand, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));

        topStatus = Ui.chip(this, "● 实时采样", 9.5f);
        topStatus.setTextColor(Theme.UP);
        topStatus.setBackground(Ui.bgDrawable(Theme.alpha(Theme.UP, 24), 0, 4));
        topStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sampler.togglePause();
                updateStatusChip();
            }
        });
        top.addView(topStatus, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        topClock = Ui.text(this, "00:00:00", 15, Theme.text, true, true);
        topClock.setPadding(Ui.dp(10), 0, Ui.dp(8), 0);
        top.addView(topClock, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView gear = Ui.text(this, "⚙", 17, Theme.textDim, false, false);
        gear.setPadding(Ui.dp(6), 0, Ui.dp(2), 0);
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        top.addView(gear, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(top, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(48)));
        root.addView(Ui.hline(this, Theme.line));

        // 页签
        LinearLayout tabs = Ui.row(this);
        tabs.setPadding(Ui.dp(8), Ui.dp(5), Ui.dp(8), Ui.dp(5));
        String[] tabNames = {"行情", "逐笔", "全表", "营收"};
        for (int i = 0; i < tabNames.length; i++) {
            final int idx = i;
            TextView chip = Ui.chip(this, tabNames[i], 11.5f);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyTab(idx);
                }
            });
            tabChips[i] = chip;
            tabs.addView(chip, new LinearLayout.LayoutParams(0, Ui.dp(28), 1));
        }
        root.addView(tabs, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.hline(this, Theme.line));

        FrameLayout content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(content);

        pages[0] = buildMarketPage();
        pages[1] = buildTapePage();
        pages[2] = buildTablePage();
        pages[3] = buildEconomyPage();
        for (int i = 0; i < pages.length; i++) {
            content.addView(pages[i], new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            pages[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        }

        // 底部状态条
        root.addView(Ui.hline(this, Theme.line));
        LinearLayout bottom = Ui.row(this);
        bottom.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        statusLeft = Ui.text(this, "", 9, Theme.textFaint, false, true);
        bottom.addView(statusLeft, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        marquee = new Marquee(this);
        marquee.setVisibility(Store.marquee() ? View.VISIBLE : View.GONE);
        bottom.addView(marquee, new LinearLayout.LayoutParams(0, Ui.dp(16), 1));
        root.addView(bottom, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        updateStatusChip();
    }

    private LinearLayout buildMarketPage() {
        LinearLayout page = Ui.col(this);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = Ui.col(this);
        col.setPadding(Ui.dp(7), Ui.dp(7), Ui.dp(7), Ui.dp(10));
        sv.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 指数条
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout strip = Ui.row(this);
        for (int i = 0; i < sampler.tickers.size(); i++) {
            Ticker tk = sampler.tickers.get(i);
            if (!tk.instr.pinned) continue;
            final String code = tk.instr.code;
            IndexCard card = new IndexCard(this);
            card.setTicker(tk);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(code, true);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(104), Ui.dp(74));
            lp.rightMargin = Ui.dp(6);
            strip.addView(card, lp);
            indexCards.add(card);
        }
        hs.addView(strip, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        col.addView(hs, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(80)));

        // 品种头
        header = new PanelHeader(this);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Ui.dp(7);
        col.addView(header.root, hp);

        // K 线周期
        LinearLayout periodRow = Ui.row(this);
        periodRow.setPadding(0, Ui.dp(7), 0, Ui.dp(5));
        for (int i = 0; i < Ticker.PERIOD_NAMES.length; i++) {
            final int idx = i;
            TextView chip = Ui.chip(this, Ticker.PERIOD_NAMES[i], 10.5f);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyPeriod(idx);
                }
            });
            periodChips[i] = chip;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(25), 1);
            lp.rightMargin = Ui.dp(4);
            periodRow.addView(chip, lp);
        }
        col.addView(periodRow, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        chart = new ChartView(this);
        col.addView(chart, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(248)));

        watch = new PanelWatch(this, sampler.tickers, new OnPick() {
            @Override
            public void onPick(String code) {
                select(code, true);
            }
        });
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wp.topMargin = Ui.dp(7);
        col.addView(watch.root, wp);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        page.addView(sv, cp);
        return page;
    }

    private LinearLayout buildTapePage() {
        LinearLayout page = Ui.col(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.col(this);
        col.setPadding(Ui.dp(7), Ui.dp(7), Ui.dp(7), Ui.dp(10));
        sv.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        tape = new PanelTape(this);
        col.addView(tape.root, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = Ui.text(this, "逐笔成交记录告警（温度 / 负载）、主频跳变、品种新高新低与运行统计。"
                + "采样周期越短，流水越密。", 9.5f, Theme.textFaint, false, false);
        note.setPadding(Ui.dp(2), Ui.dp(8), Ui.dp(2), 0);
        note.setLineSpacing(Ui.dp(2), 1f);
        col.addView(note, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        page.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private LinearLayout buildTablePage() {
        LinearLayout page = Ui.col(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.col(this);
        col.setPadding(Ui.dp(7), Ui.dp(7), Ui.dp(7), Ui.dp(10));
        sv.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout sortRow = Ui.row(this);
        sortRow.setPadding(0, 0, 0, Ui.dp(6));
        TextView label = Ui.text(this, "排序", 10, Theme.textDim, false, false);
        sortRow.addView(label, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        String[] names = {"代码", "现价", "涨跌", "成交量"};
        final int[] modes = {PanelTable.SORT_CODE, PanelTable.SORT_PRICE, PanelTable.SORT_CHG, PanelTable.SORT_VOL};
        for (int i = 0; i < names.length; i++) {
            final int mode = modes[i];
            TextView chip = Ui.chip(this, names[i], 10.5f);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    table.setSort(mode);
                    updateSortChips();
                }
            });
            sortRow.addView(chip, new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(24)));
            sortChips[i] = chip;
        }
        updateSortChips();
        col.addView(sortRow, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        table = new PanelTable(this, sampler.tickers, new OnPick() {
            @Override
            public void onPick(String code) {
                select(code, true);
                applyTab(0);
            }
        });
        col.addView(table.root, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        updateSortChips();

        TextView note = Ui.text(this, "表内数值均为本机真实采集结果；GPU 相关项在多数机型上被厂商 sysfs 权限限制，"
                + "不可读时显示「—」。", 9.5f, Theme.textFaint, false, false);
        note.setPadding(Ui.dp(2), Ui.dp(8), Ui.dp(2), 0);
        note.setLineSpacing(Ui.dp(2), 1f);
        col.addView(note, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        page.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private LinearLayout buildEconomyPage() {
        LinearLayout page = Ui.col(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = Ui.col(this);
        col.setPadding(Ui.dp(7), Ui.dp(7), Ui.dp(7), Ui.dp(10));
        sv.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout toggleRow = Ui.row(this);
        toggleRow.setPadding(0, 0, 0, Ui.dp(6));
        TextView label = Ui.text(this, "结算开关", 10, Theme.textDim, false, false);
        toggleRow.addView(label, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Switch sw = new Switch(this);
        sw.setChecked(Store.economyOn());
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                eco.enabled = isChecked;
                Store.economyOn(isChecked);
                postRefresh();
            }
        });
        toggleRow.addView(sw, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView hint = Ui.text(this, "把占用率当产能：高负载赚钱，空转亏钱", 9.5f, Theme.textFaint, false, false);
        hint.setPadding(Ui.dp(8), 0, 0, 0);
        toggleRow.addView(hint, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        col.addView(toggleRow, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ecoPanel = new PanelEconomy(this, new Runnable() {
            @Override
            public void run() {
                eco.reset();
                persistEconomy();
                postRefresh();
            }
        });
        col.addView(ecoPanel.root, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        page.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    private void updateSortChips() {
        int[] modes = {PanelTable.SORT_CODE, PanelTable.SORT_PRICE, PanelTable.SORT_CHG, PanelTable.SORT_VOL};
        if (table == null) return;
        for (int i = 0; i < sortChips.length; i++) {
            if (sortChips[i] == null) continue;
            Ui.setChipState(sortChips[i], modes[i] == table.sortMode());
        }
    }

    // ---------------------------------------------------------------- 交互

    private void applyTab(int idx) {
        tabIdx = idx;
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            if (tabChips[i] != null) Ui.setChipState(tabChips[i], i == idx);
        }
        Store.tab(idx);
        postRefresh();
    }

    private void applyPeriod(int idx) {
        periodIdx = idx;
        chart.setPeriod(idx);
        for (int i = 0; i < periodChips.length; i++) {
            if (periodChips[i] != null) Ui.setChipState(periodChips[i], i == idx);
        }
        Store.period(idx);
    }

    private void select(String code, boolean persist) {
        Ticker t = sampler.byCode.get(code);
        if (t == null) {
            code = "000002";
            t = sampler.byCode.get(code);
        }
        if (t == null) return;
        selectedCode = code;
        header.setTicker(t);
        chart.setTicker(t);
        watch.setSelected(code);
        if (table != null) table.setSelected(code);
        sampler.setFocus(code);
        for (int i = 0; i < indexCards.size(); i++) {
            IndexCard card = indexCards.get(i);
            card.setSelected(card.code().equals(code));
        }
        if (persist) Store.selected(code);
        postRefresh();
    }

    private void updateStatusChip() {
        if (topStatus == null) return;
        if (sampler == null) return;
        if (sampler.paused) {
            topStatus.setText("❚❚ 已暂停");
            topStatus.setTextColor(Theme.gold);
            topStatus.setBackground(Ui.bgDrawable(Theme.alpha(Theme.gold, 24), 0, 4));
        } else {
            topStatus.setText("● 实时 " + fmtInterval(Store.intervalMs()));
            topStatus.setTextColor(Theme.UP);
            topStatus.setBackground(Ui.bgDrawable(Theme.alpha(Theme.UP, 24), 0, 4));
        }
    }

    private String fmtInterval(int ms) {
        if (ms % 1000 == 0) return (ms / 1000) + "s";
        return String.format(Locale.US, "%.1fs", ms / 1000.0);
    }

    // ---------------------------------------------------------------- 刷新

    private void postRefresh() {
        if (refreshPending) return;
        refreshPending = true;
        ui.post(new Runnable() {
            @Override
            public void run() {
                refreshPending = false;
                refreshUi();
            }
        });
    }

    private void refreshUi() {
        if (root == null) return;
        topClock.setText(clockFmt.format(new Date()));

        long now = android.os.SystemClock.elapsedRealtime();
        long up = sampler.startedAt > 0 ? (now - sampler.startedAt) / 1000 : 0;
        if (up < 0) up = 0;
        statusLeft.setText("采样 " + fmtInterval(Store.intervalMs())
                + " · 运行 " + String.format(Locale.US, "%02d:%02d", up / 60, up % 60)
                + " · 线程 " + Probe.safeThreads()
                + " · 品种 " + sampler.tickers.size());

        Snap s = sampler.snap;
        if (s != null && Store.marquee()) {
            marquee.setText(buildMarquee(s));
        }

        header.refresh();
        if (tabIdx == 0) {
            chart.invalidate();
            watch.refresh();
        } else if (tabIdx == 1) {
            tape.refresh(sampler.tapeSnapshot(22));
        } else if (tabIdx == 2) {
            table.refresh();
        } else {
            ecoPanel.refresh(eco);
        }
        for (int i = 0; i < indexCards.size(); i++) indexCards.get(i).invalidate();
    }

    private long SystemClockElapsed() {
        return android.os.SystemClock.elapsedRealtime();
    }

    private String buildMarquee(Snap s) {
        StringBuilder sb = new StringBuilder();
        Ticker load = sampler.byCode.get("000002");
        Ticker mem = sampler.byCode.get("000003");
        Ticker idx = sampler.byCode.get("000001");
        Ticker gt = sampler.byCode.get("000302");
        Ticker bt = sampler.byCode.get("000501");
        Ticker ct = sampler.byCode.get("000503");
        Ticker net = sampler.byCode.get("000601");
        Ticker ctxt = sampler.byCode.get("000701");
        append(sb, "CPU 负载", load);
        append(sb, "CPU 频率", idx);
        append(sb, "内存", mem);
        append(sb, "GPU 负载", gt);
        append(sb, "电池温度", bt);
        append(sb, "CPU 温度", ct);
        append(sb, "网络下行", net);
        append(sb, "上下文切换", ctxt);
        return sb.toString();
    }

    private void append(StringBuilder sb, String label, Ticker t) {
        if (t == null) return;
        if (sb.length() > 0) sb.append("   ·   ");
        sb.append(label).append(" ").append(t.instr.fmt(t.last)).append(t.instr.unit);
        double ch = t.change();
        if (!Double.isNaN(ch) && Math.abs(ch) > 1e-9) {
            sb.append(" ").append(t.instr.fmtSigned(ch));
        }
    }

    // ---------------------------------------------------------------- 设置 / 关于

    private interface ChipSink {
        void onPick(int value);
    }

    private void openSettings() {
        LinearLayout box = Ui.col(this);
        box.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        box.setBackgroundColor(Theme.panel);

        box.addView(sectionLabel("采样周期"));
        box.addView(chipRow(new String[]{"0.5s", "1s", "2s", "5s", "10s"},
                new int[]{500, 1000, 2000, 5000, 10000}, Store.intervalMs(), new ChipSink() {
                    @Override
                    public void onPick(int value) {
                        sampler.setInterval(value);
                        Store.intervalMs(value);
                        updateStatusChip();
                        postRefresh();
                    }
                }));

        box.addView(sectionLabel("配色"));
        box.addView(chipRow(new String[]{"红涨绿跌", "绿涨红跌"},
                new int[]{1, 0}, Store.redUp() ? 1 : 0, new ChipSink() {
                    @Override
                    public void onPick(int value) {
                        Store.redUp(value == 1);
                        Theme.apply(Store.theme() == 0, Store.redUp());
                        recreate();
                    }
                }));

        box.addView(sectionLabel("界面主题"));
        box.addView(chipRow(new String[]{"深色", "浅色"},
                new int[]{0, 1}, Store.theme(), new ChipSink() {
                    @Override
                    public void onPick(int value) {
                        Store.theme(value);
                        Theme.apply(value == 0, Store.redUp());
                        recreate();
                    }
                }));

        box.addView(sectionLabel("底部滚动信息"));
        box.addView(switchRow(Store.marquee(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Store.marquee(isChecked);
                marquee.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        }));

        box.addView(sectionLabel("营收系统"));
        box.addView(switchRow(Store.economyOn(), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Store.economyOn(isChecked);
                eco.enabled = isChecked;
            }
        }));

        TextView about = Ui.text(this, "关于 · 运行环境与数据口径  ›", 12, Theme.accent, false, false);
        about.setPadding(0, Ui.dp(12), 0, Ui.dp(6));
        about.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAbout();
            }
        });
        box.addView(about, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        b.setTitle("设置");
        b.setView(box);
        b.setNegativeButton("关闭", null);
        b.show();
    }

    private TextView sectionLabel(String s) {
        TextView v = Ui.text(this, s, 10, Theme.textDim, false, false);
        v.setPadding(0, Ui.dp(10), 0, Ui.dp(4));
        return v;
    }

    private LinearLayout chipRow(final String[] labels, final int[] values, int current, final ChipSink sink) {
        LinearLayout row = Ui.row(this);
        final TextView[] views = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView v = Ui.chip(this, labels[i], 11f);
            Ui.setChipState(v, values[i] == current);
            final int idx = i;
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    sink.onPick(values[idx]);
                    for (int j = 0; j < views.length; j++) {
                        Ui.setChipState(views[j], values[j] == values[idx]);
                    }
                }
            });
            views[i] = v;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(30), 1);
            lp.rightMargin = Ui.dp(5);
            row.addView(v, lp);
        }
        return row;
    }

    private LinearLayout switchRow(boolean checked, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = Ui.row(this);
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw, Ui.lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void openAbout() {
        StringBuilder sb = new StringBuilder();
        sb.append("性能行情终端 · PerfTicker for Android\n");
        sb.append("版本 1.0.0-android\n\n");
        sb.append("开发者 QiuSui · 组织 QiuSui_Studio\n");
        sb.append("开源协议 GPL-3.0\n");
        sb.append("原作 Windows 版：https://github.com/QiuSui1145/PerfTicker\n");
        sb.append("本工程是其 GPL-3.0 授权的安卓移植版，保留原作者署名与协议。\n\n");

        sb.append("【运行环境】\n");
        sb.append("Android ").append(android.os.Build.VERSION.RELEASE)
                .append("（API ").append(android.os.Build.VERSION.SDK_INT).append("）\n");
        sb.append("设备 ").append(Probe.deviceSummary()).append("\n");
        sb.append("架构 ").append(abi()).append("\n\n");

        sb.append("【硬件配置】\n");
        sb.append("CPU ").append(Probe.cpuModel()).append(" · ").append(Probe.coreCount()).append(" 核\n");
        sb.append("GPU ").append(Probe.gpuModel()).append("\n");
        sb.append("内存 ").append(Sampler.fmt(Probe.ramTotal(), 1)).append(" GB\n");
        sb.append("存储 ").append(Sampler.fmt(Probe.storageTotal(), 1)).append(" GB\n\n");

        sb.append("【营收基准】\n");
        sb.append(eco.weightText()).append("\n\n");

        sb.append("【数据口径】\n");
        sb.append("· CPU 占用 / 频率：/proc/stat、sysfs cpufreq\n");
        sb.append("· 内存：/proc/meminfo\n");
        sb.append("· 电池温度 / 电量：BatteryManager 广播\n");
        sb.append("· 机身温度：/sys/class/thermal\n");
        sb.append("· 存储：StatFs（数据分区）\n");
        sb.append("· 网络上下行：TrafficStats 差值\n");
        sb.append("· 存储 IO：/proc/diskstats 扇区差值\n");
        sb.append("· 成交量：/proc/stat 上下文切换\n");
        sb.append("· GPU：厂商 sysfs，多数机型对第三方 App 不可读\n\n");

        sb.append("【与原版的差异】\n");
        sb.append("· 原版 52 项指标（含逐进程 GPU、全量进程表）依赖 Windows 私有接口；\n");
        sb.append("  Android 无法枚举其它 App 进程，故进程表改为全品种行情表。\n");
        sb.append("· 原版托盘 / 桌面挂件为 Windows 形态，安卓版以后台暂停采样替代。\n");
        sb.append("· 全部采集均为只读查询，不修改系统状态。\n");

        TextView tv = Ui.text(this, sb.toString(), 10.5f, Theme.text, false, false);
        tv.setLineSpacing(Ui.dp(3), 1f);
        tv.setMovementMethod(ScrollingMovementMethod.getInstance());
        tv.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));

        ScrollView sv = new ScrollView(this);
        sv.addView(tv);

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
        b.setTitle("关于");
        b.setView(sv);
        b.setPositiveButton("知道了", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        b.show();
    }

    private String abi() {
        String[] abis = android.os.Build.SUPPORTED_ABIS;
        return abis != null && abis.length > 0 ? abis[0] : "未知";
    }
}
