package studio.qiusui.perfticker;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * 采样线程 + 行情派生：把一次 Snap 分发给每个品种，生成逐笔成交 / 告警事件，驱动营收结算。
 * 对应原工程 Probes.cs 的采样循环 + AppForm.cs 的事件生成。
 */
final class Sampler implements Runnable {

    interface Listener {
        void onSample(Snap s);

        void onTrade(Trade t);
    }

    final ArrayList<Ticker> tickers = new ArrayList<Ticker>();
    final HashMap<String, Ticker> byCode = new HashMap<String, Ticker>();
    final ArrayList<Trade> tape = new ArrayList<Trade>();
    final Economy eco;

    Listener listener;
    volatile boolean running;
    volatile boolean paused;
    volatile int intervalMs = 1000;

    Snap snap;
    long startedAt;
    long sampleCount;

    private Thread thread;
    private String focus = "000002";
    private double prevIndexGhz = Double.NaN;
    private long lastJumpTs;
    private long lastStatTs;
    private final HashMap<String, Long> alertTs = new HashMap<String, Long>();
    private final HashMap<String, Double> prevHigh = new HashMap<String, Double>();
    private final HashMap<String, Double> prevLow = new HashMap<String, Double>();

    Sampler(Economy eco, int intervalMs) {
        this.eco = eco;
        this.intervalMs = intervalMs;
        ArrayList<Instr> list = Catalog.build();
        for (int i = 0; i < list.size(); i++) {
            Instr ins = list.get(i);
            Ticker t = new Ticker(ins);
            tickers.add(t);
            byCode.put(ins.code, t);
        }
    }

    void setListener(Listener l) {
        listener = l;
    }

    synchronized void start() {
        if (thread != null) return;
        running = true;
        paused = false;
        startedAt = SystemClock.elapsedRealtime();
        addTrade(System.currentTimeMillis(), "行情开盘 · " + tickers.size() + " 个品种接入", Double.NaN);
        thread = new Thread(this, "perf-sampler");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    void setInterval(int ms) {
        intervalMs = ms;
    }

    void togglePause() {
        paused = !paused;
        addTrade(System.currentTimeMillis(), paused ? "行情暂停 · 采样挂起" : "行情恢复 · 采样继续", Double.NaN);
    }

    void setFocus(String code) {
        focus = code;
    }

    @Override
    public void run() {
        while (running) {
            long t0 = SystemClock.elapsedRealtime();
            try {
                if (!paused) doSample();
            } catch (Throwable ignored) {
            }
            long cost = SystemClock.elapsedRealtime() - t0;
            long wait = intervalMs - cost;
            if (wait < 40) wait = 40;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void doSample() {
        Snap s = Probe.sample();
        snap = s;
        long ts = s.ts;
        double dt = intervalMs / 1000.0;
        double vol = Double.isNaN(s.ctxtPerSec) ? 0 : s.ctxtPerSec * dt;

        for (int i = 0; i < tickers.size(); i++) {
            Ticker t = tickers.get(i);
            t.push(ts, Catalog.valueOf(s, t.instr.src), vol);
        }
        sampleCount++;

        detectEvents(s, ts);
        eco.settle(s, dt);

        Listener l = listener;
        if (l != null) l.onSample(s);
    }

    // ---------------------------------------------------------------- 事件

    private void detectEvents(Snap s, long ts) {
        double idx = s.cpuAvgGhz;
        if (!Double.isNaN(idx) && !Double.isNaN(prevIndexGhz)) {
            double d = idx - prevIndexGhz;
            if (Math.abs(d) >= 0.15 && ts - lastJumpTs > 3000) {
                lastJumpTs = ts;
                String head = d > 0 ? "CPU 主频拉升 " : "CPU 主频回落 ";
                addTrade(ts, head + sign(d, 2) + "GHz → " + fmt(idx, 2) + "GHz", d);
            }
        }
        if (!Double.isNaN(idx)) prevIndexGhz = idx;

        if (s.cpuPct >= 88) alert("cpu", ts, "CPU 负载告警 " + fmt(s.cpuPct, 1) + "%（阈值 88%）", 1);
        if (s.memPct >= 90) alert("mem", ts, "内存占用告警 " + fmt(s.memPct, 1) + "%（阈值 90%）", 1);
        if (s.battTemp >= 42) alert("batt", ts, "电池温度告警 " + fmt(s.battTemp, 1) + "°C（阈值 42°C）", 1);
        if (s.cpuTemp >= 74) alert("cput", ts, "CPU 温度告警 " + fmt(s.cpuTemp, 1) + "°C（阈值 74°C）", 1);
        if (s.gpuPct >= 92) alert("gpu", ts, "GPU 负载告警 " + fmt(s.gpuPct, 1) + "%（阈值 92%）", 1);

        Ticker f = byCode.get(focus);
        if (f != null && !Double.isNaN(f.last)) {
            double ph = prevHigh.containsKey(focus) ? prevHigh.get(focus) : Double.NaN;
            double pl = prevLow.containsKey(focus) ? prevLow.get(focus) : Double.NaN;
            if (!Double.isNaN(ph) && f.last > ph + 1e-9) {
                addTrade(ts, f.instr.code + " " + f.instr.name + " 创日内新高 " + f.instr.fmt(f.last) + f.instr.unit, f.last - ph);
            }
            if (!Double.isNaN(pl) && f.last < pl - 1e-9) {
                addTrade(ts, f.instr.code + " " + f.instr.name + " 创日内新低 " + f.instr.fmt(f.last) + f.instr.unit, f.last - pl);
            }
            prevHigh.put(focus, f.high);
            prevLow.put(focus, f.low);
        }

        if (lastStatTs == 0) lastStatTs = ts;
        if (ts - lastStatTs >= 60000) {
            lastStatTs = ts;
            String c = Double.isNaN(s.ctxtPerSec) ? "—" : fmt(s.ctxtPerSec / 1000.0, 1) + "K/s";
            addTrade(ts, "运行统计 · 采样 " + sampleCount + " 次 · 上下文切换 " + c, Double.NaN);
        }
    }

    private void alert(String key, long ts, String text, int kind) {
        Long last = alertTs.get(key);
        if (last != null && ts - last < 20000) return;
        alertTs.put(key, ts);
        addTrade(ts, text, kind == 1 ? 1.0 : -1.0);
    }

    void addTrade(long ts, String text, double delta) {
        int kind = Double.isNaN(delta) ? Trade.NEUTRAL : (delta > 0 ? Trade.UP : delta < 0 ? Trade.DOWN : Trade.NEUTRAL);
        Trade t = new Trade(ts, text, kind);
        synchronized (tape) {
            tape.add(0, t);
            while (tape.size() > 200) tape.remove(tape.size() - 1);
        }
        Listener l = listener;
        if (l != null) l.onTrade(t);
    }

    ArrayList<Trade> tapeSnapshot(int max) {
        ArrayList<Trade> out = new ArrayList<Trade>();
        synchronized (tape) {
            for (int i = 0; i < tape.size() && out.size() < max; i++) out.add(tape.get(i));
        }
        return out;
    }

    // ---------------------------------------------------------------- 工具

    static double nan() {
        return Double.NaN;
    }

    static String fmt(double v, int dec) {
        if (Double.isNaN(v)) return "—";
        return String.format(Locale.US, "%." + dec + "f", v);
    }

    static String sign(double v, int dec) {
        if (Double.isNaN(v)) return "—";
        String s = String.format(Locale.US, "%." + dec + "f", Math.abs(v));
        return (v > 0 ? "+" : v < 0 ? "-" : "") + s;
    }
}
