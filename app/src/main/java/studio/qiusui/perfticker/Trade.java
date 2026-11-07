package studio.qiusui.perfticker;

/** 逐笔成交 / 事件流水。 */
final class Trade {
    static final int NEUTRAL = 0;
    static final int UP = 1;
    static final int DOWN = 2;

    final long ts;
    final String text;
    final int kind;

    Trade(long ts, String text, int kind) {
        this.ts = ts;
        this.text = text;
        this.kind = kind;
    }
}
