package com.family.studyguard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * 在线题库同步：从家长填的地址下载 questions.txt，校验后缓存到应用私有目录。
 * 失败绝不破坏现有题库（本地 600 题永远兜底）。
 */
public final class RemoteBank {

    private static final int MAX_BYTES = 4 * 1024 * 1024;   // 上限 4MB，足够 2 万题

    public interface Callback {
        void onResult(boolean ok, int count, String msg);
    }

    private RemoteBank() {
    }

    public static File cacheFile(Context c) {
        return new File(c.getFilesDir(), "questions_remote.txt");
    }

    /** 清掉在线缓存，回到内置题库。 */
    public static void clear(Context c) {
        File f = cacheFile(c);
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        Prefs.setRemoteResult(c, 0);
        QuestionBank.reload(c);
    }

    public static void sync(final Context ctx, final String url, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        final Handler ui = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                int n = 0;
                String msg;
                try {
                    if (url == null || url.trim().isEmpty()) throw new IOException("没有填写地址");
                    String text = fetch(url.trim());
                    List<Question> list = QuestionBank.parse(text);
                    if (list.size() < QuestionBank.MIN_REMOTE) {
                        throw new IOException("只解析到 " + list.size() + " 题，至少需要 "
                                + QuestionBank.MIN_REMOTE + " 题");
                    }
                    File target = cacheFile(app);
                    File tmp = new File(target.getAbsolutePath() + ".tmp");
                    FileOutputStream out = new FileOutputStream(tmp);
                    try {
                        out.write(text.getBytes("UTF-8"));
                        out.getFD().sync();
                    } finally {
                        out.close();
                    }
                    if (target.exists() && !target.delete()) throw new IOException("无法覆盖旧缓存");
                    if (!tmp.renameTo(target)) throw new IOException("无法写入缓存");

                    n = list.size();
                    ok = true;
                    Prefs.setRemoteResult(app, n);
                    QuestionBank.reload(app);
                    msg = "已同步 " + n + " 题";
                } catch (Throwable t) {
                    msg = "同步失败：" + (t.getMessage() == null
                            ? t.getClass().getSimpleName() : t.getMessage());
                }
                final boolean fOk = ok;
                final int fN = n;
                final String fMsg = msg;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cb != null) cb.onResult(fOk, fN, fMsg);
                    }
                });
            }
        }, "remote-bank-sync").start();
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "text/plain,*/*");
        conn.setRequestProperty("User-Agent", "StudyGuard/1.0");

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }

        InputStream in = conn.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int r;
        try {
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > MAX_BYTES) throw new IOException("文件超过 4MB");
                bos.write(buf, 0, r);
            }
        } finally {
            in.close();
            conn.disconnect();
        }
        return new String(bos.toByteArray(), "UTF-8");
    }
}
