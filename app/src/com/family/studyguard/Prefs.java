package com.family.studyguard;

import android.content.Context;
import android.content.SharedPreferences;

/** 家长的设置项，全部存在 SharedPreferences 里，改配置不用重装 APK。 */
public final class Prefs {
    private static final String FILE = "guard";
    private static final String K_ENABLED   = "enabled";
    private static final String K_INTERVAL  = "interval_min";
    private static final String K_PIN       = "pin";
    private static final String K_SUSPEND   = "suspend_until";
    private static final String K_SOLVED    = "solved_count";
    private static final String K_EXPLAIN  = "explain_min";
    private static final String K_REMOTE_URL = "remote_url";
    private static final String K_REMOTE_AT  = "remote_at";
    private static final String K_REMOTE_N   = "remote_n";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        return sp(c).getBoolean(K_ENABLED, true);
    }

    public static void setEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean(K_ENABLED, v).apply();
    }

    /** 出题间隔，默认 5 分钟。 */
    public static int intervalMin(Context c) {
        return sp(c).getInt(K_INTERVAL, 1);
    }

    public static void setIntervalMin(Context c, int v) {
        if (v < 1) v = 1;
        if (v > 120) v = 120;
        sp(c).edit().putInt(K_INTERVAL, v).apply();
    }

    /** 家长密码，默认 1234。 */
    public static String pin(Context c) {
        return sp(c).getString(K_PIN, "1234");
    }

    public static void setPin(Context c, String v) {
        sp(c).edit().putString(K_PIN, v).apply();
    }

    /** 家长临时放行到期时间戳；0 表示没有放行。 */
    public static long suspendUntil(Context c) {
        return sp(c).getLong(K_SUSPEND, 0L);
    }

    public static void setSuspendUntil(Context c, long t) {
        sp(c).edit().putLong(K_SUSPEND, t).apply();
    }

    /** 答对后强制阅读解析的分钟数，0 表示不强制（2.4 秒放行）。 */
    public static int explainMin(Context c) {
        return sp(c).getInt(K_EXPLAIN, 1);
    }

    public static void setExplainMin(Context c, int v) {
        if (v < 0) v = 0;
        if (v > 10) v = 10;
        sp(c).edit().putInt(K_EXPLAIN, v).apply();
    }

    /** 在线题库地址，留空表示不用在线题库。 */
    public static String remoteUrl(Context c) {
        return sp(c).getString(K_REMOTE_URL, "");
    }

    public static void setRemoteUrl(Context c, String v) {
        sp(c).edit().putString(K_REMOTE_URL, v == null ? "" : v.trim()).apply();
    }

    public static long remoteAt(Context c) {
        return sp(c).getLong(K_REMOTE_AT, 0L);
    }

    public static int remoteCount(Context c) {
        return sp(c).getInt(K_REMOTE_N, 0);
    }

    public static void setRemoteResult(Context c, int count) {
        sp(c).edit().putLong(K_REMOTE_AT, System.currentTimeMillis())
                .putInt(K_REMOTE_N, count).apply();
    }

    public static int solvedCount(Context c) {
        return sp(c).getInt(K_SOLVED, 0);
    }

    public static void bumpSolved(Context c) {
        sp(c).edit().putInt(K_SOLVED, solvedCount(c) + 1).apply();
    }
}
