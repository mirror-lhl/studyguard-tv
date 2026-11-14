package com.family.studyguard;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 题库。优先用"在线题库"同步下来的缓存，没有或无效时回退到 APK 内置的 600 题。
 *
 * 文本格式（一行一题）：
 *   科目|题干|A|B|C|D|正确序号(0~3)|解析
 */
public final class QuestionBank {

    private static final String ASSET = "questions.txt";
    public static final int MIN_REMOTE = 50;

    private static final List<Question> ALL = new ArrayList<>();
    private static boolean loaded = false;
    private static final Random RND = new Random();
    private static volatile boolean usingRemote = false;

    private QuestionBank() {
    }

    public static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;

        // 1) 在线题库缓存
        File remoteFile = RemoteBank.cacheFile(c);
        if (remoteFile.exists()) {
            try {
                List<Question> remote = parse(readFile(remoteFile));
                if (remote.size() >= MIN_REMOTE) {
                    ALL.addAll(remote);
                    usingRemote = true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2) 内置题库兜底
        if (ALL.isEmpty()) {
            try {
                ALL.addAll(parse(readAsset(c)));
            } catch (Throwable ignored) {
            }
            usingRemote = false;
        }

        if (ALL.isEmpty()) {
            ALL.add(new Question("数学", "2 + 2 等于？",
                    new String[]{"3", "4", "5", "6"}, 1, "2 + 2 = 4。"));
        }
    }

    /** 同步成功后重新载入。 */
    public static synchronized void reload(Context c) {
        ALL.clear();
        loaded = false;
        load(c);
    }

    public static boolean isUsingRemote() {
        return usingRemote;
    }

    public static synchronized int size(Context c) {
        load(c);
        return ALL.size();
    }

    public static synchronized int countOf(Context c, String subject) {
        load(c);
        int n = 0;
        for (Question q : ALL) if (q.subject.equals(subject)) n++;
        return n;
    }

    /** 随机取一题，尽量避开最近做过的 avoidRecent 道。 */
    public static synchronized Question pick(Context c, int avoidRecent) {
        load(c);
        int n = ALL.size();
        if (n == 0) return null;
        int cap = Math.min(LAST.length, Math.max(1, n - 1));
        if (avoidRecent > cap) avoidRecent = cap;

        Set<Integer> recent = new HashSet<>();
        for (int i = 0; i < avoidRecent; i++) {
            int v = LAST[(pos - i + LAST.length) % LAST.length];
            if (v >= 0 && v < n) recent.add(v);
        }
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!recent.contains(i)) pool.add(i);
        if (pool.isEmpty()) for (int i = 0; i < n; i++) pool.add(i);

        int idx = pool.get(RND.nextInt(pool.size()));
        LAST[pos] = idx;
        pos = (pos + 1) % LAST.length;
        return ALL.get(idx);
    }

    // ------------------------------------------------------------------ 解析

    /** 解析题库文本；格式不对的行直接跳过，不抛异常。 */
    public static List<Question> parse(String text) {
        List<Question> out = new ArrayList<>();
        if (text == null) return out;
        if (text.startsWith("\ufeff")) text = text.substring(1);
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\|", -1);
            if (f.length < 8) continue;
            int ans;
            try {
                ans = Integer.parseInt(f[6].trim());
            } catch (Exception e) {
                continue;
            }
            if (ans < 0 || ans > 3) continue;
            String[] opts = {f[2], f[3], f[4], f[5]};
            if (opts[0].isEmpty() || opts[1].isEmpty() || opts[2].isEmpty() || opts[3].isEmpty()) continue;
            out.add(new Question(f[0].trim(), f[1].trim(), opts, ans, f[7].trim()));
        }
        return out;
    }

    private static String readAsset(Context c) throws IOException {
        InputStream in = c.getAssets().open(ASSET);
        try {
            return new String(readAll(in), "UTF-8");
        } finally {
            in.close();
        }
    }

    private static String readFile(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return new String(readAll(in), "UTF-8");
        } finally {
            in.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    // 最近出过的题号，避免连着重复
    private static final int[] LAST = new int[16];
    private static int pos = 0;
}
