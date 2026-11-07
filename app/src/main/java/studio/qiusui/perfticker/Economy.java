package studio.qiusui.perfticker;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * 经济系统（算力经营）。公式与原工程 Economy.cs 完全一致：
 *   净收益(元/s) = 满载收益 × (占用率/100)^1.7 − 空转成本
 *   空转成本     = 满载收益 × (盈亏平衡点/100)^1.7
 * 过热降效：电池温度达到阈值后 CPU / GPU 收益打折。
 */
final class Economy {

    static final String[] UNITS = {"CPU 综合", "GPU 引擎", "内存", "存储 IO", "网络"};
    static final double[] CAP = {22.0, 30.0, 12.0, 7.2, 7.2};
    static final double[] BREAK_EVEN = {30, 28, 35, 25, 25};

    final double[] weight = new double[5];
    final double[] load = new double[5];
    final double[] net = new double[5];

    double total;
    double peak;
    long seconds;
    double netPerSec;

    boolean enabled;

    final ArrayDeque<Double> history = new ArrayDeque<Double>();

    Economy() {
        load[0] = Double.NaN;
        load[1] = Double.NaN;
        load[2] = Double.NaN;
        load[3] = Double.NaN;
        load[4] = Double.NaN;

        double cores = Probe.coreCount();
        double ram = Probe.ramTotal();
        double disk = Probe.storageTotal();

        weight[0] = clamp(cores / 16.0);
        weight[1] = 1.0; // GPU 显存不可读取，按基准 1.0 计
        weight[2] = clamp(ram / 32.0);
        weight[3] = clamp(disk / 512.0);
        weight[4] = 1.0; // 网络为吞吐口径，不折算
    }

    private static double clamp(double v) {
        if (Double.isNaN(v)) return 1.0;
        if (v < 0.3) return 0.3;
        if (v > 4.0) return 4.0;
        return v;
    }

    static double netOf(double cap, double loadPct, double bePct, double derate) {
        if (Double.isNaN(loadPct)) return 0;
        double p = Math.max(0, Math.min(100, loadPct)) / 100.0;
        double be = bePct / 100.0;
        return cap * Math.pow(p, 1.7) * derate - cap * Math.pow(be, 1.7);
    }

    void settle(Snap s, double dtSec) {
        if (s == null) return;
        load[0] = s.cpuPct;
        load[1] = s.gpuPct;
        load[2] = s.memPct;
        load[3] = Double.isNaN(s.diskLoadPct) ? s.storagePct : s.diskLoadPct;
        load[4] = s.netLoadPct;

        double temp = Double.isNaN(s.battTemp) ? Double.NaN : s.battTemp;
        double derate = 1.0;
        if (!Double.isNaN(temp)) {
            if (temp >= 45) derate = 0.78;
            else if (temp >= 42) derate = 0.88;
            else if (temp >= 38) derate = 0.95;
        }

        double sum = 0;
        for (int i = 0; i < 5; i++) {
            net[i] = netOf(CAP[i] * weight[i], load[i], BREAK_EVEN[i], derate);
            sum += net[i];
        }
        netPerSec = sum;
        history.addLast(sum);
        while (history.size() > 80) history.removeFirst();

        if (!enabled || dtSec <= 0) return;
        total += sum * dtSec;
        seconds += (long) Math.max(0, Math.round(dtSec));
        if (total > peak) peak = total;
    }

    void reset() {
        total = 0;
        peak = 0;
        seconds = 0;
        netPerSec = 0;
        history.clear();
    }

    ArrayList<Double> historyList() {
        ArrayList<Double> out = new ArrayList<Double>();
        for (Double d : history) out.add(d);
        return out;
    }

    String weightText() {
        return String.format("CPU %.2f× · GPU %.2f× · 内存 %.2f× · 存储 %.2f× · 网络 %.2f×",
                weight[0], weight[1], weight[2], weight[3], weight[4]);
    }
}
