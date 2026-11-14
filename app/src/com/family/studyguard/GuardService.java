package com.family.studyguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * 常驻前台服务：计时、弹题、收答题结果。
 * 计时用“每 5 秒检查一次”的方式而不是 postDelayed(5 分钟)，
 * 这样电视休眠/唤醒后时间不会错乱。
 */
public class GuardService extends Service {

    public static final String ACTION_START = "com.family.studyguard.action.START";
    public static final String ACTION_STOP = "com.family.studyguard.action.STOP";
    public static final String ACTION_TEST = "com.family.studyguard.action.TEST";

    private static final String CHANNEL = "guard";
    private static final int NOTI_ID = 2001;
    private static final long TICK_MS = 5000L;

    private Handler handler;
    private QuizOverlay overlay;
    private MediaPauser pauser;
    private long lastQuizAt = 0L;
    private boolean running = false;

    private final QuizScreen.Listener quizListener = new QuizScreen.Listener() {
        @Override
        public void onSolved() {
            hideQuiz();
            Prefs.bumpSolved(GuardService.this);
            lastQuizAt = System.currentTimeMillis();
            updateNotification();
        }

        @Override
        public void onParentUnlock() {
            hideQuiz();
            Prefs.setSuspendUntil(GuardService.this,
                    System.currentTimeMillis() + 30 * 60 * 1000L);
            lastQuizAt = System.currentTimeMillis();
            updateNotification();
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, GuardService.class);
        i.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    public static void stop(Context c) {
        Intent i = new Intent(c, GuardService.class);
        i.setAction(ACTION_STOP);
        c.startService(i);
    }

    public static void testNow(Context c) {
        Intent i = new Intent(c, GuardService.class);
        i.setAction(ACTION_TEST);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        overlay = new QuizOverlay(this);
        pauser = new MediaPauser(this);
        startForegroundSafe();
        running = true;
        maybeSyncRemote();
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            running = false;
            if (overlay != null) overlay.hide();
            if (pauser != null) pauser.resume();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_TEST.equals(action)) {
            showQuiz(true);
            return START_STICKY;
        }
        if (!running) {
            running = true;
            handler.post(tick);
        }
        startForegroundSafe();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (overlay != null) overlay.hide();
        if (pauser != null) pauser.resume();
        super.onDestroy();
    }

    /** 每天最多同步一次在线题库。 */
    private void maybeSyncRemote() {
        final String url = Prefs.remoteUrl(this);
        if (url == null || url.isEmpty()) return;
        long last = Prefs.remoteAt(this);
        if (System.currentTimeMillis() - last < 24 * 3600 * 1000L) return;
        RemoteBank.sync(this, url, null);
    }

    // ------------------------------------------------------------------ 计时

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                check();
            } catch (Throwable ignored) {
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private void check() {
        if (!Prefs.enabled(this)) {
            stopSelf();
            return;
        }
        long now = System.currentTimeMillis();
        if (overlay.isShowing()) return;
        if (now < Prefs.suspendUntil(this)) return;
        if (!isScreenOn()) return;

        long interval = Prefs.intervalMin(this) * 60_000L;
        if (lastQuizAt == 0L) {
            lastQuizAt = now;
            updateNotification();
            return;
        }
        if (now - lastQuizAt < interval) return;

        // 家长正在设置界面时不要弹，免得挡住自己
        if (MainActivity.isForeground) {
            lastQuizAt = now;
            return;
        }
        showQuiz(false);
    }

    private void showQuiz(boolean force) {
        if (overlay.isShowing()) return;
        Question q = QuestionBank.pick(this, 8);

        pauser.pause();

        if (overlay.canShow() && overlay.show(q, quizListener)) {
            lastQuizAt = System.currentTimeMillis();
            return;
        }

        // 没有悬浮窗权限：退回 Activity 方案
        pauser.resume();
        lastQuizAt = System.currentTimeMillis();
        try {
            QuizActivity.launch(this);
        } catch (Throwable ignored) {
        }
    }

    private void hideQuiz() {
        overlay.hide();
        pauser.resume();
    }

    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return pm.isInteractive();
        }
        return pm.isScreenOn();
    }

    // ------------------------------------------------------------------ 通知

    private void startForegroundSafe() {
        Notification n = buildNotification();
        try {
            startForeground(NOTI_ID, n);
        } catch (Throwable ignored) {
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL, "学习守卫", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }

        int interval = Prefs.intervalMin(this);
        String text = "每 " + interval + " 分钟一题 · 已答对 " + Prefs.solvedCount(this) + " 题";
        return b.setContentTitle("学习守卫运行中")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTI_ID, buildNotification());
        } catch (Throwable ignored) {
        }
    }
}
