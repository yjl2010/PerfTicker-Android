package studio.qiusui.perfticker;

import java.util.ArrayList;

/**
 * 指标目录：按本机实际核数生成品种。
 * 板块划分沿用原工程的「指数 / 处理器 / 图形 / 存储 / 温度 / 网络 / 系统」。
 */
final class Catalog {

    static final String G_INDEX = "指数";
    static final String G_CORE = "处理器";
    static final String G_GPU = "图形";
    static final String G_DISK = "存储";
    static final String G_TEMP = "温度";
    static final String G_NET = "网络";
    static final String G_SYS = "系统";

    private Catalog() {
    }

    static ArrayList<Instr> build() {
        ArrayList<Instr> list = new ArrayList<Instr>();
        int cores = Probe.coreCount();

        list.add(new Instr("000001", "CPU 综合指数", "GHz", 2, G_INDEX, "cpu.index", "freq", true));
        list.add(new Instr("000002", "CPU 负载", "%", 1, G_INDEX, "cpu.load", "load", true));
        list.add(new Instr("000003", "内存占用", "%", 1, G_INDEX, "mem.pct", "pct", true));

        for (int i = 0; i < cores; i++) {
            String code = String.format("0001%02d", i + 1);
            list.add(new Instr(code, String.format("核心 %02d", i + 1), "GHz", 2, G_CORE,
                    "core" + i + ".freq", "freq", false));
        }

        list.add(new Instr("000301", "GPU 核心频率", "MHz", 0, G_GPU, "gpu.freq", "freq", false));
        list.add(new Instr("000302", "GPU 负载", "%", 1, G_GPU, "gpu.load", "load", true));
        list.add(new Instr("000303", "GPU 温度", "°C", 0, G_GPU, "gpu.temp", "temp", false));

        list.add(new Instr("000401", "存储占用", "%", 1, G_DISK, "disk.pct", "pct", false));
        list.add(new Instr("000402", "可用空间", "GB", 1, G_DISK, "disk.free", "count", false));
        list.add(new Instr("000403", "存储 IO", "MB/s", 2, G_DISK, "disk.io", "speed", false));

        list.add(new Instr("000501", "电池温度", "°C", 1, G_TEMP, "batt.temp", "temp", true));
        list.add(new Instr("000502", "电池电量", "%", 0, G_TEMP, "batt.level", "pct", false));
        list.add(new Instr("000503", "CPU 温度", "°C", 1, G_TEMP, "cpu.temp", "temp", true));

        list.add(new Instr("000601", "网络下行", "MB/s", 2, G_NET, "net.rx", "speed", true));
        list.add(new Instr("000602", "网络上行", "MB/s", 2, G_NET, "net.tx", "speed", false));

        list.add(new Instr("000701", "上下文切换", "K/s", 1, G_SYS, "sys.ctxt", "speed", true));
        list.add(new Instr("000702", "线程数", "个", 0, G_SYS, "sys.thread", "count", false));
        list.add(new Instr("000703", "运行时长", "分", 0, G_SYS, "sys.uptime", "count", false));

        return list;
    }

    static double valueOf(Snap s, String src) {
        if (s == null) return Double.NaN;
        if ("cpu.index".equals(src)) return s.cpuAvgGhz;
        if ("cpu.load".equals(src)) return s.cpuPct;
        if ("mem.pct".equals(src)) return s.memPct;
        if ("gpu.freq".equals(src)) return s.gpuMhz;
        if ("gpu.load".equals(src)) return s.gpuPct;
        if ("gpu.temp".equals(src)) return s.gpuTemp;
        if ("disk.pct".equals(src)) return s.storagePct;
        if ("disk.free".equals(src)) return s.storageFreeGb;
        if ("disk.io".equals(src)) return s.diskMb;
        if ("batt.temp".equals(src)) return s.battTemp;
        if ("batt.level".equals(src)) return s.battLevel;
        if ("cpu.temp".equals(src)) return s.cpuTemp;
        if ("net.rx".equals(src)) return s.rxMb;
        if ("net.tx".equals(src)) return s.txMb;
        if ("sys.ctxt".equals(src)) return Double.isNaN(s.ctxtPerSec) ? Double.NaN : s.ctxtPerSec / 1000.0;
        if ("sys.thread".equals(src)) return s.threads <= 0 ? Double.NaN : s.threads;
        if ("sys.uptime".equals(src)) return s.uptimeS / 60.0;
        if (src.startsWith("core") && src.endsWith(".freq")) {
            int idx = parseIntSafe(src.substring(4, src.length() - 5));
            if (idx >= 0 && idx < s.coreGhz.length) return s.coreGhz[idx];
            return Double.NaN;
        }
        return Double.NaN;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return -1;
        }
    }
}
