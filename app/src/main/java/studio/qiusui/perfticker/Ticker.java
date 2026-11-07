package studio.qiusui.perfticker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * 一只品种的行情状态：现价 / 今开 / 最高 / 最低 / 均价 / 成交量，
 * 以及 5 个周期的 K 线聚合（采样点即 tick，按周期聚合成 OHLC）。
 */
final class Ticker {

    static final long[] PERIODS = {2000L, 5000L, 10000L, 30000L, 60000L};
    static final String[] PERIOD_NAMES = {"2秒K", "5秒K", "10秒K", "30秒K", "1分K"};
    static final int MAX_BARS = 140;

    final Instr instr;

    double open = Double.NaN;
    double last = Double.NaN;
    double prev = Double.NaN;
    double high = Double.NaN;
    double low = Double.NaN;
    private double sum;
    private long cnt;
    double volume;
    long lastTs;

    final ArrayList<ArrayDeque<Candle>> series = new ArrayList<ArrayDeque<Candle>>();

    Ticker(Instr instr) {
        this.instr = instr;
        for (int i = 0; i < PERIODS.length; i++) {
            series.add(new ArrayDeque<Candle>());
        }
    }

    synchronized void push(long ts, double v, double volDelta) {
        if (Double.isNaN(v)) return;
        prev = last;
        last = v;
        lastTs = ts;
        if (Double.isNaN(open)) {
            open = v;
            high = v;
            low = v;
        }
        if (v > high || Double.isNaN(high)) high = v;
        if (v < low || Double.isNaN(low)) low = v;
        sum += v;
        cnt++;
        volume += volDelta;

        for (int i = 0; i < PERIODS.length; i++) {
            long p = PERIODS[i];
            long bucket = ts / p;
            ArrayDeque<Candle> dq = series.get(i);
            Candle tip = dq.peekLast();
            if (tip == null || tip.bucket != bucket) {
                tip = new Candle(bucket, bucket * p, v);
                dq.addLast(tip);
                while (dq.size() > MAX_BARS) dq.pollFirst();
            }
            tip.add(v, volDelta);
        }
    }

    synchronized boolean hasData() {
        return cnt > 0;
    }

    synchronized double avg() {
        return cnt == 0 ? Double.NaN : sum / cnt;
    }

    synchronized double change() {
        if (Double.isNaN(last) || Double.isNaN(open)) return Double.NaN;
        return last - open;
    }

    synchronized double changePct() {
        if (Double.isNaN(last) || Double.isNaN(open) || Math.abs(open) < 1e-9) return Double.NaN;
        return (last - open) / open * 100.0;
    }

    synchronized double amplitude() {
        if (Double.isNaN(high) || Double.isNaN(low) || Double.isNaN(open) || Math.abs(open) < 1e-9) return Double.NaN;
        return (high - low) / open * 100.0;
    }

    /** 复制指定周期的 K 线，末根为最新。 */
    synchronized ArrayList<Candle> bars(int periodIdx, int max) {
        ArrayList<Candle> out = new ArrayList<Candle>();
        ArrayDeque<Candle> dq = series.get(periodIdx);
        int skip = dq.size() - max;
        if (skip < 0) skip = 0;
        int i = 0;
        for (Iterator<Candle> it = dq.iterator(); it.hasNext(); ) {
            Candle c = it.next();
            if (i++ < skip) continue;
            out.add(copy(c));
        }
        return out;
    }

    static Candle copy(Candle c) {
        Candle n = new Candle(c.bucket, c.ts, c.o);
        n.h = c.h;
        n.l = c.l;
        n.c = c.c;
        n.v = c.v;
        return n;
    }

    synchronized int barCount(int periodIdx) {
        return series.get(periodIdx).size();
    }

    synchronized double lastBarOpen(int periodIdx) {
        Candle c = series.get(periodIdx).peekLast();
        return c == null ? Double.NaN : c.o;
    }
}
