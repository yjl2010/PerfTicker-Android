package studio.qiusui.perfticker;

/** 一根 K 线。bucket 为周期编号，ts 为该周期首毫秒。 */
final class Candle {
    long bucket;
    long ts;
    double o;
    double h;
    double l;
    double c;
    double v;

    Candle(long bucket, long ts, double price) {
        this.bucket = bucket;
        this.ts = ts;
        this.o = price;
        this.h = price;
        this.l = price;
        this.c = price;
        this.v = 0;
    }

    boolean bull() {
        return c >= o;
    }

    void add(double price, double vol) {
        if (price > h) h = price;
        if (price < l) l = price;
        c = price;
        v += vol;
    }
}
