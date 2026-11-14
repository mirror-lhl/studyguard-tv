package com.family.studyguard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 无障碍服务：盯着"卸载/停用设备管理器"这类系统弹窗。
 * 一旦出现针对本应用的卸载或停用，先按返回键取消，再弹出家长密码闸门。
 */
public class GuardAccessibilityService extends AccessibilityService {

    private static volatile long sBypassUntil = 0L;
    private static volatile long sCooldownUntil = 0L;

    /** 家长输对密码后，放行一段时间（避免连环弹窗）。 */
    public static void grant(long millis) {
        sBypassUntil = System.currentTimeMillis() + millis;
    }

    public static boolean bypassed() {
        return System.currentTimeMillis() < sBypassUntil;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        long now = System.currentTimeMillis();
        if (event == null || bypassed()) return;
        if (now < sCooldownUntil) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        CharSequence pn = event.getPackageName();
        if (pn == null) return;
        String pkg = pn.toString();
        boolean watched = pkg.contains("packageinstaller")
                || pkg.contains("settings")
                || pkg.contains("securitycenter")
                || pkg.contains("providers.settings");
        if (!watched) return;

        // 顶层窗口若是我们自己的密码页，就别再触发
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null
                && getPackageName().contentEquals(root.getPackageName())) {
            return;
        }

        String text = collect(root, event);
        if (text.isEmpty()) return;

        String low = text.toLowerCase();
        boolean ours = text.contains("学习守卫") || low.contains("studyguard");
        boolean uninstall = text.contains("卸载") || low.contains("uninstall");
        boolean deactivate = text.contains("取消激活") || text.contains("停用设备管理")
                || low.contains("deactivate") || low.contains("remove admin");

        boolean uninstallOurs = ours && uninstall;
        boolean deactivateOurs = ours && deactivate;

        if (uninstallOurs || deactivateOurs) {
            sCooldownUntil = now + 3000;   // 3 秒内不再重复拦截
            performGlobalAction(GLOBAL_ACTION_BACK);
            Intent i = new Intent(this, PinGateActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }

    private String collect(AccessibilityNodeInfo root, AccessibilityEvent e) {
        StringBuilder sb = new StringBuilder();
        if (e.getText() != null) {
            for (CharSequence c : e.getText()) sb.append(c).append(' ');
        }
        if (e.getContentDescription() != null) {
            sb.append(e.getContentDescription()).append(' ');
        }
        if (root != null) walk(root, sb, 0);
        return sb.toString();
    }

    private void walk(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (n == null || depth > 30) return;
        if (n.getText() != null) sb.append(n.getText()).append(' ');
        if (n.getContentDescription() != null) sb.append(n.getContentDescription()).append(' ');
        int count = n.getChildCount();
        for (int i = 0; i < count; i++) walk(n.getChild(i), sb, depth + 1);
    }

    @Override
    public void onInterrupt() {
    }
}
