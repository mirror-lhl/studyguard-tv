package com.family.studyguard;

import android.app.Service;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * 用系统悬浮窗把整个屏幕盖住：底层视频继续存在，但遥控器与触摸都被这一层吃掉。
 */
public class QuizOverlay {

    private final Service svc;
    private final WindowManager wm;
    private View root;
    private QuizScreen screen;

    public QuizOverlay(Service svc) {
        this.svc = svc;
        this.wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean canShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(svc);
        }
        return true;
    }

    public boolean show(Question q, QuizScreen.Listener listener) {
        if (!canShow()) return false;
        hide();

        screen = new QuizScreen(svc, q, listener);
        root = screen.createView();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = 0;
        lp.setTitle("StudyGuardQuiz");

        try {
            wm.addView(root, lp);
        } catch (Throwable t) {
            root = null;
            return false;
        }
        root.requestFocus();
        root.post(new Runnable() {
            @Override
            public void run() {
                if (screen != null) screen.focusFirst();
            }
        });
        return true;
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    public boolean isShowing() {
        return root != null;
    }

    public void hide() {
        if (root == null) return;
        try {
            wm.removeView(root);
        } catch (Throwable ignored) {
        }
        root = null;
        screen = null;
    }
}
