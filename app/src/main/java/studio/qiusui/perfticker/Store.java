package studio.qiusui.perfticker;

import android.content.Context;
import android.content.SharedPreferences;

/** 设置持久化，对应原工程 Settings.cs（%LOCALAPPDATA%\PerfTicker\settings.json）。 */
final class Store {

    private static SharedPreferences sp;

    private Store() {
    }

    static void init(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("perfticker", Context.MODE_PRIVATE);
    }

    static int intervalMs() {
        return sp.getInt("intervalMs", 1000);
    }

    static void intervalMs(int v) {
        sp.edit().putInt("intervalMs", v).apply();
    }

    /** 0 深色 / 1 浅色 */
    static int theme() {
        return sp.getInt("theme", 0);
    }

    static void theme(int v) {
        sp.edit().putInt("theme", v).apply();
    }

    static boolean redUp() {
        return sp.getBoolean("redUp", true);
    }

    static void redUp(boolean v) {
        sp.edit().putBoolean("redUp", v).apply();
    }

    static boolean marquee() {
        return sp.getBoolean("marquee", true);
    }

    static void marquee(boolean v) {
        sp.edit().putBoolean("marquee", v).apply();
    }

    static boolean economyOn() {
        return sp.getBoolean("economyOn", false);
    }

    static void economyOn(boolean v) {
        sp.edit().putBoolean("economyOn", v).apply();
    }

    static String selected() {
        return sp.getString("selected", "000002");
    }

    static void selected(String code) {
        sp.edit().putString("selected", code).apply();
    }

    static int period() {
        return sp.getInt("period", 1);
    }

    static void period(int v) {
        sp.edit().putInt("period", v).apply();
    }

    static int tab() {
        return sp.getInt("tab", 0);
    }

    static void tab(int v) {
        sp.edit().putInt("tab", v).apply();
    }

    static double ecoTotal() {
        return sp.getFloat("ecoTotal", 0f);
    }

    static void ecoTotal(double v) {
        sp.edit().putFloat("ecoTotal", (float) v).apply();
    }

    static double ecoPeak() {
        return sp.getFloat("ecoPeak", 0f);
    }

    static void ecoPeak(double v) {
        sp.edit().putFloat("ecoPeak", (float) v).apply();
    }

    static long ecoSeconds() {
        return sp.getLong("ecoSeconds", 0);
    }

    static void ecoSeconds(long v) {
        sp.edit().putLong("ecoSeconds", v).apply();
    }
}
