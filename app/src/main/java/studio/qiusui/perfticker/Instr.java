package studio.qiusui.perfticker;

import java.util.Locale;

/**
 * 一只「个股」：一项系统指标。
 * code 沿用交易所风格 6 位代码，便于列表排版。
 */
final class Instr {

    final String code;
    final String name;
    final String unit;
    final int decimals;
    final String group;
    /** 数据源键，见 Probe / Sampler 映射 */
    final String src;
    /** 品种类型：freq 频率 / load 负载 / temp 温度 / pct 占比 / speed 速率 / count 计数 */
    final String kind;
    /** 是否进入顶部指数条 */
    final boolean pinned;

    Instr(String code, String name, String unit, int decimals, String group, String src, String kind, boolean pinned) {
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.decimals = decimals;
        this.group = group;
        this.src = src;
        this.kind = kind;
        this.pinned = pinned;
    }

    String fmt(double v) {
        if (Double.isNaN(v)) return "—";
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    String fmtSigned(double v) {
        if (Double.isNaN(v)) return "—";
        String s = String.format(Locale.US, "%." + decimals + "f", Math.abs(v));
        return (v > 0 ? "+" : v < 0 ? "-" : "") + s;
    }

    String withUnit(double v) {
        return fmt(v) + unit;
    }
}
