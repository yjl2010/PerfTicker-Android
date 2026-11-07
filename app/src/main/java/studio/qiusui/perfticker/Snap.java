package studio.qiusui.perfticker;

/** 一次采样的不可变快照，对应原工程 Snapshot.cs。 */
final class Snap {

    double[] corePct = new double[0];
    double[] coreGhz = new double[0];
    double[] coreMaxGhz = new double[0];

    double cpuPct = Double.NaN;
    double cpuAvgGhz = Double.NaN;
    double cpuMaxGhz = Double.NaN;

    double memPct = Double.NaN;
    double memUsedGb = Double.NaN;
    double memTotalGb = Double.NaN;

    double battTemp = Double.NaN;
    double battLevel = Double.NaN;

    double cpuTemp = Double.NaN;
    double gpuTemp = Double.NaN;

    double gpuPct = Double.NaN;
    double gpuMhz = Double.NaN;

    double storagePct = Double.NaN;
    double storageFreeGb = Double.NaN;
    double storageTotalGb = Double.NaN;

    double rxMb = Double.NaN;
    double txMb = Double.NaN;

    double diskMb = Double.NaN;

    double ctxtPerSec = Double.NaN;
    int threads;
    double uptimeS;

    /** 各经营单元折算后的负载百分比（0-100），供营收结算使用 */
    double netLoadPct = Double.NaN;
    double diskLoadPct = Double.NaN;

    long ts;
}
