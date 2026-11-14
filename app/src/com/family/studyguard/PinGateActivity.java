package com.family.studyguard;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 家长密码闸门：拦截卸载/停用时弹出，输对密码才放行 5 分钟。 */
public class PinGateActivity extends Activity {

    private final StringBuilder buf = new StringBuilder();
    private TextView dots;
    private TextView hint;
    private Button firstKey;
    private int fails = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0B0E12);

        TextView title = text("家长验证", 26, 0xFFFFFFFF, true);
        root.addView(title, lp(0, 6));

        TextView sub = text("输入家长密码，继续卸载/停用操作", 15, 0xFF8A94A6, false);
        root.addView(sub, lp(0, 16));

        dots = text("", 30, 0xFF4DA3FF, true);
        dots.setLetterSpacing(0.4f);
        root.addView(dots, lp(0, 6));
        refreshDots();

        hint = text("", 14, 0xFFFF6B6B, false);
        root.addView(hint, lp(0, 10));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER);
        String[][] keys = {
                {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"清除", "0", "取消"}
        };
        for (String[] row : keys) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER);
            for (final String k : row) {
                Button b = key(k);
                if (firstKey == null) firstKey = b;
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(78), dp(52));
                l.setMargins(dp(6), dp(5), dp(6), dp(5));
                r.addView(b, l);
            }
            pad.addView(r);
        }
        root.addView(pad, lp(dp(10), 0));

        setContentView(root);
        if (firstKey != null) {
            firstKey.requestFocus();
        }
    }

    /** 支持数字键直接输入（带数字键的遥控器 / adb input keyevent）。 */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int c = event.getKeyCode();
            if (c >= KeyEvent.KEYCODE_0 && c <= KeyEvent.KEYCODE_9) {
                press(String.valueOf(c - KeyEvent.KEYCODE_0));
                return true;
            }
            if (c == KeyEvent.KEYCODE_DEL) {
                press("清除");
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void press(String k) {
        if ("取消".equals(k)) {
            finish();
            return;
        }
        if ("清除".equals(k)) {
            buf.setLength(0);
            hint.setText("");
        } else if (buf.length() < 4) {
            buf.append(k);
        }
        refreshDots();
        if (buf.length() == 4) check();
    }

    private void check() {
        if (buf.toString().equals(Prefs.pin(this))) {
            GuardAccessibilityService.grant(5 * 60 * 1000L);
            Toast.makeText(this, "已放行 5 分钟", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            fails++;
            buf.setLength(0);
            refreshDots();
            if (fails >= 3) {
                hint.setText("错误次数过多，已拦截");
                Toast.makeText(this, "已拦截", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                hint.setText("密码错误，还可试 " + (3 - fails) + " 次");
            }
        }
    }

    private void refreshDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buf.length(); i++) sb.append('●');
        for (int i = buf.length(); i < 4; i++) sb.append('○');
        dots.setText(sb.toString());
    }

    private Button key(final String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.length() > 1 ? 14 : 20);
        b.setAllCaps(false);
        b.setTextColor(0xFFE8EEF7);
        b.setBackground(bg());
        b.setFocusable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                press(label);
            }
        });
        return b;
    }

    private StateListDrawable bg() {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, round(0xFF2F80ED, 0xFF8CC0FF));
        d.addState(new int[]{}, round(0xFF161B24, 0xFF2B3546));
        return d;
    }

    private GradientDrawable round(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(2), stroke);
        return g;
    }

    private LinearLayout.LayoutParams lp(int top, int bottom) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.gravity = Gravity.CENTER_HORIZONTAL;
        l.topMargin = top;
        l.bottomMargin = bottom;
        return l;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
