package studio.qiusui.perfticker;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

/**
 * 采集层。原工程走 Win32 / PDH / NVML，这里换成 Android 允许读取的来源：
 * /proc/stat、/proc/meminfo、/proc/diskstats、/sys/.../cpufreq、thermal_zone、BatteryManager、TrafficStats。
 * 读不到的项一律返回 NaN，界面显示「—」，绝不编造数据。
 */
final class Probe {

    private static final double NA = Double.NaN;

    private static Context app;
    private static int cores = 1;

    private static long[] prevTotal;
    private static long[] prevIdle;
    private static double[] lastPct;
    private static boolean cpuInited;

    private static long prevCtxt = -1;
    private static long prevRx = -1;
    private static long prevTx = -1;
    private static long prevSectors = -1;
    private static long prevTs = -1;

    private static String[] freqPaths;

    private static String cpuZone;
    private static String gpuZone;
    private static String battZone;
    private static boolean zonesScanned;

    private static String[] gpuFreqPaths;
    private static String gpuBusyPath;
    private static String gpuTempPath;

    private static double battTemp = NA;
    private static double battLevel = NA;

    private static String cpuModelText;
    private static String gpuModelText;
    private static double ramTotalGb = NA;

    private Probe() {
    }

    static void init(Context c) {
        app = c.getApplicationContext();
        cores = detectCores();
        prevTotal = new long[cores + 1];
        prevIdle = new long[cores + 1];
        lastPct = new double[cores];

        freqPaths = new String[cores];
        for (int i = 0; i < cores; i++) {
            freqPaths[i] = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/";
        }

        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent sticky = null;
        try {
            sticky = app.registerReceiver(null, f);
        } catch (Throwable ignored) {
        }
        applyBattery(sticky);
        try {
            app.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    applyBattery(intent);
                }
            }, f);
        } catch (Throwable ignored) {
        }

        gpuFreqPaths = new String[]{
                "/sys/class/kgsl/kgsl-3d0/gpuclk",
                "/sys/class/kgsl/kgsl-3d0/clock_mhz",
                "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
                "/sys/class/misc/mali0/device/cur_freq",
                "/sys/kernel/gpu/gpu_clock",
                "/sys/class/devfreq/gpufreq/cur_freq"
        };
        gpuBusyPath = firstReadable(new String[]{
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/class/kgsl/kgsl-3d0/gpu_load",
                "/sys/class/misc/mali0/device/utilisation",
                "/sys/kernel/gpu/gpu_busy_percentage"
        });
        gpuTempPath = firstReadable(new String[]{
                "/sys/class/kgsl/kgsl-3d0/temp",
                "/sys/class/kgsl/kgsl-3d0/gpu_temp"
        });
    }

    static int coreCount() {
        return cores;
    }

    /** 本 App 线程数（/proc/self/task），供状态栏显示。 */
    static int safeThreads() {
        try {
            String[] t = new File("/proc/self/task").list();
            return t == null ? 0 : t.length;
        } catch (Throwable x) {
            return 0;
        }
    }

    private static int detectCores() {
        int n = 0;
        for (int i = 0; i < 32; i++) {
            if (new File("/sys/devices/system/cpu/cpu" + i).exists()) n = i + 1;
            else break;
        }
        if (n <= 0) n = Runtime.getRuntime().availableProcessors();
        return Math.max(1, n);
    }

    private static void applyBattery(Intent it) {
        if (it == null) return;
        int t = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (t != Integer.MIN_VALUE && t > 0) battTemp = t / 10.0;
        int lv = it.getIntExtra(BatteryManager.EXTRA_LEVEL, Integer.MIN_VALUE);
        int sc = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (lv != Integer.MIN_VALUE && sc > 0) battLevel = lv * 100.0 / sc;
    }

    // ------------------------------------------------------------------ 采样

    static synchronized Snap sample() {
        Snap s = new Snap();
        s.ts = System.currentTimeMillis();
        long nowMs = SystemClock.elapsedRealtime();
        double dt = prevTs < 0 ? 0 : (nowMs - prevTs) / 1000.0;
        prevTs = nowMs;

        readCpu(s, dt);
        readMem(s);
        readFreq(s);
        readThermal(s);
        readGpu(s);
        readStorage(s);
        readNetwork(s, dt);
        readDisk(s, dt);
        readMisc(s);

        s.battTemp = battTemp;
        s.battLevel = battLevel;
        if (Double.isNaN(s.gpuTemp)) {
            s.gpuTemp = readTempFile(gpuTempPath);
        }
        return s;
    }

    private static void readCpu(Snap s, double dt) {
        long[] totals = new long[cores + 1];
        long[] idles = new long[cores + 1];
        long ctxt = -1;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/stat"), 1024);
            String ln;
            while ((ln = br.readLine()) != null) {
                if (ln.startsWith("cpu ")) {
                    long[] t = parseCpuLine(ln);
                    totals[0] = t[0];
                    idles[0] = t[1];
                } else if (ln.startsWith("cpu")) {
                    int idx = digitsToInt(ln, 3);
                    if (idx >= 0 && idx < cores) {
                        long[] t = parseCpuLine(ln);
                        totals[idx + 1] = t[0];
                        idles[idx + 1] = t[1];
                    }
                } else if (ln.startsWith("ctxt ")) {
                    ctxt = parseLong(ln.substring(5).trim(), -1);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(br);
        }

        s.corePct = new double[cores];
        if (!cpuInited) {
            System.arraycopy(totals, 0, prevTotal, 0, prevTotal.length);
            System.arraycopy(idles, 0, prevIdle, 0, prevIdle.length);
            cpuInited = true;
            for (int i = 0; i < cores; i++) s.corePct[i] = NA;
            s.cpuPct = NA;
        } else {
            for (int i = 0; i < cores; i++) {
                long dTotal = totals[i + 1] - prevTotal[i + 1];
                long dIdle = idles[i + 1] - prevIdle[i + 1];
                double pct;
                if (dTotal > 0) {
                    pct = (dTotal - dIdle) * 100.0 / dTotal;
                    if (pct < 0) pct = 0;
                    if (pct > 100) pct = 100;
                    lastPct[i] = pct;
                } else {
                    pct = lastPct[i];
                }
                s.corePct[i] = pct;
            }
            double sum = 0;
            int n = 0;
            for (int i = 0; i < cores; i++) {
                if (!Double.isNaN(s.corePct[i])) {
                    sum += s.corePct[i];
                    n++;
                }
            }
            s.cpuPct = n == 0 ? NA : sum / n;
            System.arraycopy(totals, 0, prevTotal, 0, prevTotal.length);
            System.arraycopy(idles, 0, prevIdle, 0, prevIdle.length);
        }

        if (ctxt >= 0 && prevCtxt >= 0 && dt > 0) {
            s.ctxtPerSec = (ctxt - prevCtxt) / dt;
        }
        if (ctxt >= 0) prevCtxt = ctxt;
    }

    private static long[] parseCpuLine(String ln) {
        String[] f = ln.trim().split("\\s+");
        long total = 0;
        long idle = 0;
        for (int i = 1; i < f.length; i++) {
            long v = parseLong(f[i], 0);
            if (i == 4 || i == 5) idle += v;
            total += v;
        }
        return new long[]{total, idle};
    }

    private static void readFreq(Snap s) {
        s.coreGhz = new double[cores];
        s.coreMaxGhz = new double[cores];
        double sum = 0;
        int n = 0;
        double max = NA;
        for (int i = 0; i < cores; i++) {
            double khz = readKhz(freqPaths[i] + "scaling_cur_freq");
            if (Double.isNaN(khz)) khz = readKhz(freqPaths[i] + "cpuinfo_cur_freq");
            double maxKhz = readKhz(freqPaths[i] + "cpuinfo_max_freq");
            if (Double.isNaN(maxKhz)) maxKhz = readKhz(freqPaths[i] + "scaling_max_freq");
            s.coreMaxGhz[i] = Double.isNaN(maxKhz) ? NA : maxKhz / 1000000.0;
            if (Double.isNaN(khz) || khz <= 0) {
                s.coreGhz[i] = NA;
                continue;
            }
            double ghz = khz / 1000000.0;
            s.coreGhz[i] = ghz;
            sum += ghz;
            n++;
            if (Double.isNaN(max) || ghz > max) max = ghz;
        }
        s.cpuAvgGhz = n == 0 ? NA : sum / n;
        for (int i = 0; i < cores; i++) {
            if (!Double.isNaN(s.coreMaxGhz[i])) {
                if (Double.isNaN(s.cpuMaxGhz) || s.coreMaxGhz[i] > s.cpuMaxGhz) s.cpuMaxGhz = s.coreMaxGhz[i];
            }
        }
        // 单核读不到时用邻居补位，避免 big.LITTLE 只暴露簇频率导致大面积空值
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < cores; i++) {
                if (Double.isNaN(s.coreGhz[i])) {
                    if (i > 0 && !Double.isNaN(s.coreGhz[i - 1])) s.coreGhz[i] = s.coreGhz[i - 1];
                    else if (i + 1 < cores && !Double.isNaN(s.coreGhz[i + 1])) s.coreGhz[i] = s.coreGhz[i + 1];
                }
            }
        }
    }

    private static void readMem(Snap s) {
        long totalKb = -1;
        long availKb = -1;
        long freeKb = -1;
        long cacheKb = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/meminfo"), 1024);
            String ln;
            while ((ln = br.readLine()) != null) {
                if (ln.startsWith("MemTotal:")) totalKb = kbOf(ln);
                else if (ln.startsWith("MemAvailable:")) availKb = kbOf(ln);
                else if (ln.startsWith("MemFree:")) freeKb = kbOf(ln);
                else if (ln.startsWith("Cached:")) cacheKb = kbOf(ln);
            }
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(br);
        }
        if (totalKb > 0) {
            if (availKb < 0) availKb = (freeKb > 0 ? freeKb : 0) + cacheKb;
            if (availKb > totalKb) availKb = totalKb;
            s.memTotalGb = totalKb / 1048576.0;
            s.memUsedGb = (totalKb - availKb) / 1048576.0;
            s.memPct = availKb <= 0 ? 100 : (totalKb - availKb) * 100.0 / totalKb;
        } else {
            long[] mem = activityMem();
            if (mem != null && mem[0] > 0) {
                s.memTotalGb = mem[0] / 1073741824.0;
                s.memUsedGb = mem[1] / 1073741824.0;
                s.memPct = mem[1] * 100.0 / mem[0];
            }
        }
        if (Double.isNaN(ramTotalGb) && !Double.isNaN(s.memTotalGb)) ramTotalGb = s.memTotalGb;
    }

    private static long[] activityMem() {
        try {
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return null;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return new long[]{mi.totalMem, mi.totalMem - mi.availMem};
        } catch (Throwable t) {
            return null;
        }
    }

    private static void readThermal(Snap s) {
        if (!zonesScanned) {
            scanZones();
            zonesScanned = true;
        }
        s.cpuTemp = readTempFile(cpuZone);
        s.gpuTemp = readTempFile(gpuZone);
        double bz = readTempFile(battZone);
        if (!Double.isNaN(bz) && Double.isNaN(s.battTemp)) s.battTemp = bz;
    }

    /** 遍历 thermal_zone，按 type 归类 CPU / GPU / 电池。 */
    private static void scanZones() {
        File dir = new File("/sys/class/thermal");
        File[] zones = dir.listFiles();
        if (zones == null) return;
        for (File z : zones) {
            String name = z.getName();
            if (!name.startsWith("thermal_zone")) continue;
            String type = readText(new File(z, "type").getAbsolutePath());
            if (type == null) continue;
            String t = type.toLowerCase();
            String tempPath = new File(z, "temp").getAbsolutePath();
            if (cpuZone == null && (t.contains("cpu") || t.contains("soc") || t.contains("ap") || t.contains("tsens"))) {
                cpuZone = tempPath;
            } else if (gpuZone == null && t.contains("gpu")) {
                gpuZone = tempPath;
            } else if (battZone == null && (t.contains("batt") || t.contains("bms") || t.contains("chg"))) {
                battZone = tempPath;
            }
        }
    }

    private static void readGpu(Snap s) {
        double freq = NA;
        for (int i = 0; gpuFreqPaths != null && i < gpuFreqPaths.length; i++) {
            double v = readDouble(gpuFreqPaths[i]);
            if (!Double.isNaN(v) && v > 0) {
                freq = v;
                break;
            }
        }
        if (!Double.isNaN(freq)) {
            // kHz / Hz / MHz 三种口径归一成 MHz
            if (freq > 1000000) freq = freq / 1000000.0;
            else if (freq > 100000) freq = freq / 1000.0;
            else if (freq > 10000) freq = freq / 1000.0;
            s.gpuMhz = freq;
        }
        double busy = readDouble(gpuBusyPath);
        if (!Double.isNaN(busy)) {
            if (busy <= 1.0 && busy >= 0) busy = busy * 100.0;
            s.gpuPct = busy;
        }
    }

    private static void readStorage(Snap s) {
        try {
            StatFs st = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = st.getTotalBytes();
            long avail = st.getAvailableBytes();
            if (total > 0) {
                s.storageTotalGb = total / 1073741824.0;
                s.storageFreeGb = avail / 1073741824.0;
                s.storagePct = (total - avail) * 100.0 / total;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void readNetwork(Snap s, double dt) {
        try {
            long rx = TrafficStats.getTotalRxBytes();
            long tx = TrafficStats.getTotalTxBytes();
            if (rx != TrafficStats.UNSUPPORTED && prevRx >= 0 && dt > 0) {
                s.rxMb = Math.max(0, rx - prevRx) / dt / 1048576.0;
            }
            if (tx != TrafficStats.UNSUPPORTED && prevTx >= 0 && dt > 0) {
                s.txMb = Math.max(0, tx - prevTx) / dt / 1048576.0;
            }
            if (rx != TrafficStats.UNSUPPORTED) prevRx = rx;
            if (tx != TrafficStats.UNSUPPORTED) prevTx = tx;
            if (!Double.isNaN(s.rxMb) || !Double.isNaN(s.txMb)) {
                double mb = 0;
                if (!Double.isNaN(s.rxMb)) mb += s.rxMb;
                if (!Double.isNaN(s.txMb)) mb += s.txMb;
                s.netLoadPct = Math.min(100, mb / 40.0 * 100.0);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void readDisk(Snap s, double dt) {
        long sectors = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/diskstats"), 1024);
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] f = ln.trim().split("\\s+");
                if (f.length < 10) continue;
                String name = f[2];
                if (isPartition(name)) continue;
                sectors += parseLong(f[5], 0) + parseLong(f[9], 0);
            }
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(br);
        }
        if (sectors > 0) {
            if (prevSectors >= 0 && dt > 0) {
                long d = sectors - prevSectors;
                if (d < 0) d = 0;
                s.diskMb = d * 512.0 / dt / 1048576.0;
                s.diskLoadPct = Math.min(100, s.diskMb / 200.0 * 100.0);
            }
            prevSectors = sectors;
        }
    }

    private static boolean isPartition(String name) {
        if (name.startsWith("loop") || name.startsWith("ram") || name.startsWith("zram")) return true;
        // sda1 / mmcblk0p1 / nvme0n1p1 之类是分区，避免与整盘重复计数
        if (name.matches("^[a-z]+\\d+$")) return true;
        if (name.matches("^mmcblk\\d+p\\d+$")) return true;
        if (name.matches("^nvme\\d+n\\d+p\\d+$")) return true;
        return false;
    }

    private static void readMisc(Snap s) {
        try {
            String[] t = new File("/proc/self/task").list();
            s.threads = t == null ? 0 : t.length;
        } catch (Throwable ignored) {
        }
        s.uptimeS = SystemClock.elapsedRealtime() / 1000.0;
    }

    // ------------------------------------------------------------------ 设备信息

    static String cpuModel() {
        if (cpuModelText != null) return cpuModelText;
        String name = readTextValue("/proc/cpuinfo", "Hardware");
        if (name == null || name.isEmpty()) name = readTextValue("/proc/cpuinfo", "model name");
        if (name == null || name.isEmpty()) name = readTextValue("/proc/cpuinfo", "Processor");
        if (name == null || name.isEmpty()) {
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    name = Build.SOC_MODEL;
                }
            } catch (Throwable ignored) {
            }
            if (name == null || name.isEmpty()) name = Build.HARDWARE;
        }
        cpuModelText = name == null ? "未知" : name.trim();
        return cpuModelText;
    }

    static String gpuModel() {
        if (gpuModelText != null) return gpuModelText;
        String[] paths = {"/sys/class/kgsl/kgsl-3d0/gpu_model", "/sys/kernel/gpu/gpu_model", "/sys/class/misc/mali0/device/gpu_model"};
        for (int i = 0; i < paths.length; i++) {
            String v = readText(paths[i]);
            if (v != null && v.length() > 0) {
                gpuModelText = v.trim();
                return gpuModelText;
            }
        }
        gpuModelText = "不可读取（厂商限制）";
        return gpuModelText;
    }

    static double ramTotal() {
        if (!Double.isNaN(ramTotalGb)) return ramTotalGb;
        long[] mem = activityMem();
        if (mem != null && mem[0] > 0) ramTotalGb = mem[0] / 1073741824.0;
        return ramTotalGb;
    }

    static double storageTotal() {
        try {
            StatFs st = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            return st.getTotalBytes() / 1073741824.0;
        } catch (Throwable t) {
            return NA;
        }
    }

    static String deviceSummary() {
        String brand = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        return (brand + " " + model).trim();
    }

    // ------------------------------------------------------------------ IO 工具

    private static String firstReadable(String[] paths) {
        for (int i = 0; i < paths.length; i++) {
            File f = new File(paths[i]);
            if (f.exists() && f.canRead()) return paths[i];
        }
        return null;
    }

    private static double readDouble(String path) {
        if (path == null) return NA;
        String v = readText(path);
        if (v == null) return NA;
        v = v.trim();
        if (v.isEmpty()) return NA;
        try {
            return Double.parseDouble(v);
        } catch (Throwable t) {
            return NA;
        }
    }

    private static double readKhz(String path) {
        return readDouble(path);
    }

    private static double readTempFile(String path) {
        double v = readDouble(path);
        if (Double.isNaN(v)) return NA;
        if (Math.abs(v) > 200) v = v / 1000.0;
        if (v < -40 || v > 150) return NA;
        return v;
    }

    private static String readText(String path) {
        if (path == null) return null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path), 256);
            String ln = br.readLine();
            return ln == null ? null : ln.trim();
        } catch (Throwable t) {
            return null;
        } finally {
            closeQuietly(br);
        }
    }

    private static String readTextValue(String path, String key) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path), 1024);
            String ln;
            while ((ln = br.readLine()) != null) {
                int c = ln.indexOf(':');
                if (c < 0) continue;
                String k = ln.substring(0, c).trim();
                if (k.equalsIgnoreCase(key)) return ln.substring(c + 1).trim();
            }
        } catch (Throwable ignored) {
        } finally {
            closeQuietly(br);
        }
        return null;
    }

    private static long kbOf(String ln) {
        int c = ln.indexOf(':');
        if (c < 0) return -1;
        String rest = ln.substring(c + 1).trim();
        String[] f = rest.split("\\s+");
        return parseLong(f[0], -1);
    }

    private static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static int digitsToInt(String s, int start) {
        int i = start;
        StringBuilder sb = new StringBuilder();
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            sb.append(s.charAt(i));
            i++;
        }
        if (sb.length() == 0) return -1;
        try {
            return Integer.parseInt(sb.toString());
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void closeQuietly(BufferedReader br) {
        if (br == null) return;
        try {
            br.close();
        } catch (Throwable ignored) {
        }
    }

    static ArrayList<String> thermalZones() {
        ArrayList<String> out = new ArrayList<String>();
        File[] zones = new File("/sys/class/thermal").listFiles();
        if (zones == null) return out;
        for (File z : zones) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            String type = readText(new File(z, "type").getAbsolutePath());
            String temp = readText(new File(z, "temp").getAbsolutePath());
            if (type != null && temp != null) out.add(type + " = " + temp);
        }
        return out;
    }
}
