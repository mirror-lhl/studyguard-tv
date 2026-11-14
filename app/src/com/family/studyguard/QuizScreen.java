package com.family.studyguard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 答题锁屏界面。既是悬浮窗的内容，也能直接当 Activity 的 contentView 用（兜底方案）。
 */
public class QuizScreen {

    public interface Listener {
        /** 答对了 */
        void onSolved();

        /** 家长输入正确密码，要求暂停 */
        void onParentUnlock();
    }

    private final Context ctx;
    private final Question q;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Button[] optionBtns = new Button[4];
    private final StringBuilder pinBuf = new StringBuilder();

    private FrameLayout root;
    private Button parentEntry;
    private ScrollView optionScroll;
    private ScrollView explainScroll;
    private TextView explainBody;
    private TextView countdownView;
    private int remain;
    private boolean cancelled;
    private View quizPage;
    private View pinPage;
    private TextView feedbackView;
    private TextView pinDots;
    private TextView pinHint;
    private int wrongCount = 0;
    private int pinFails = 0;
    private boolean finished = false;

    public QuizScreen(Context ctx, Question q, Listener l) {
        this.ctx = ctx;
        this.q = q;
        this.listener = l;
    }

    public View createView() {
        root = new BlockingFrame(ctx);
        root.setBackgroundColor(0xFF0B0E12);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);

        quizPage = buildQuizPage();
        pinPage = buildPinPage();
        pinPage.setVisibility(View.GONE);

        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                cancelled = true;
                ui.removeCallbacksAndMessages(null);
            }
        });
        root.addView(quizPage, full());
        root.addView(pinPage, full());

        parentEntry = tvAsButton("家长验证", 15);
        parentEntry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(pinPage);
            }
        });
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        plp.gravity = Gravity.BOTTOM | Gravity.END;
        plp.setMargins(0, 0, dp(30), dp(14));
        root.addView(parentEntry, plp);
        return root;
    }

    public void focusFirst() {
        if (optionBtns[0] != null) optionBtns[0].requestFocus();
    }

    // ------------------------------------------------------------------ 答题页

    private LinearLayout buildQuizPage() {
        LinearLayout page = column();
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(130), dp(26), dp(130), dp(16));

        TextView tag = tv(q.subject + " · 答对才能继续看", 16, 0xFF4DA3FF, false);
        tag.setLetterSpacing(0.08f);
        tag.setPadding(0, 0, 0, dp(10));
        page.addView(tag);

        TextView stem = tv(q.stem, 28, 0xFFFFFFFF, true);
        stem.setLineSpacing(dp(5), 1.0f);
        stem.setPadding(0, 0, 0, dp(20));
        page.addView(stem);

        LinearLayout optionBox = column();
        for (int i = 0; i < 4; i++) {
            optionBtns[i] = buildOption(i);
            optionBox.addView(optionBtns[i]);
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(false);
        scroll.addView(optionBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        page.addView(scroll, slp);
        optionScroll = scroll;

        feedbackView = tv("", 19, 0xFFFF6B6B, false);
        feedbackView.setPadding(0, dp(10), 0, 0);
        page.addView(feedbackView);

        // 答对后的“解析阅读”区域
        LinearLayout explainBox = column();
        explainBody = tv("", 22, 0xFFE8EEF7, false);
        explainBody.setLineSpacing(dp(8), 1.0f);
        explainBody.setPadding(0, dp(4), 0, dp(14));
        explainBox.addView(explainBody);

        countdownView = tv("", 18, 0xFF4DA3FF, false);
        countdownView.setPadding(0, 0, 0, dp(4));
        explainBox.addView(countdownView);

        explainScroll = new ScrollView(ctx);
        explainScroll.addView(explainBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        page.addView(explainScroll, elp);
        explainScroll.setVisibility(View.GONE);

        return page;
    }

    private Button buildOption(final int i) {
        Button b = new Button(ctx);
        b.setText(Question.LETTERS[i] + ". " + q.options[i]);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(28), 0, dp(28), 0);
        b.setBackground(optionBackground());
        b.setTextColor(0xFFE8EEF7);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.bottomMargin = dp(10);
        b.setLayoutParams(lp);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit(i, (Button) v);
            }
        });
        return b;
    }

    private void submit(int i, Button clicked) {
        if (finished) return;

        if (i == q.answer) {
            finished = true;
            startReading();
        } else {
            wrongCount++;
            clicked.setEnabled(false);
            clicked.setTextColor(0xFF5B6674);
            feedbackView.setTextColor(0xFFFF6B6B);
            feedbackView.setText(wrongCount >= 2
                    ? "✘ 又错啦，再读一遍题目，用排除法试试～"
                    : "✘ 不对哦，再想一想～");
            focusNextEnabled();
        }
    }

    /** 答对后强制阅读解析：倒计时结束才放行。 */
    private void startReading() {
        remain = Prefs.explainMin(ctx) * 60;
        if (remain <= 0) {
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    listener.onSolved();
                }
            }, 2400);
            return;
        }
        for (Button b : optionBtns) b.setEnabled(false);
        if (optionScroll != null) optionScroll.setVisibility(View.GONE);
        if (feedbackView != null) feedbackView.setVisibility(View.GONE);
        explainBody.setText("✔ 答对了！\n\n正确答案：" + Question.LETTERS[q.answer]
                + ". " + q.options[q.answer] + "\n\n" + q.explain);
        explainScroll.setVisibility(View.VISIBLE);
        countdownView.setVisibility(View.VISIBLE);
        renderCountdown();
        ui.postDelayed(tick, 1000);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (cancelled) return;
            remain--;
            renderCountdown();
            if (remain <= 0) {
                listener.onSolved();
            } else {
                ui.postDelayed(this, 1000);
            }
        }
    };

    private void renderCountdown() {
        int m = Math.max(0, remain) / 60;
        int sec = Math.max(0, remain) % 60;
        countdownView.setText(String.format("读完解析才能继续看 · 还需 %d:%02d", m, sec));
    }

    private void focusNextEnabled() {
        for (Button b : optionBtns) {
            if (b.isEnabled()) {
                b.requestFocus();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 家长页

    private View buildPinPage() {
        LinearLayout page = column();
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xFF0B0E12);

        LinearLayout card = column();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = tv("家长验证", 26, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        addCentered(card, title, 0, 6);

        TextView sub = tv("输入 4 位密码，暂停出题 30 分钟", 15, 0xFF8A94A6, false);
        sub.setGravity(Gravity.CENTER);
        addCentered(card, sub, 0, 18);

        pinDots = tv("", 30, 0xFF4DA3FF, true);
        pinDots.setLetterSpacing(0.4f);
        pinDots.setGravity(Gravity.CENTER);
        addCentered(card, pinDots, 0, 6);

        pinHint = tv("", 14, 0xFFFF6B6B, false);
        pinHint.setGravity(Gravity.CENTER);
        addCentered(card, pinHint, 0, 10);

        addCentered(card, buildKeypad(), dp(4), 0);

        Button back = tvAsButton("返回答题", 16);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(quizPage);
                focusNextEnabled();
            }
        });
        addCentered(card, back, dp(16), 0);

        return page;
    }

    private void addCentered(LinearLayout parent, View child, int topMargin, int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = topMargin;
        lp.bottomMargin = bottomMargin;
        parent.addView(child, lp);
    }

    private LinearLayout buildKeypad() {
        LinearLayout pad = column();
        pad.setGravity(Gravity.CENTER);

        for (int row = 0; row < 4; row++) {
            LinearLayout r = new LinearLayout(ctx);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER);
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                String label;
                if (idx < 9) {
                    label = String.valueOf(idx + 1);
                } else if (idx == 9) {
                    label = "清除";
                } else if (idx == 10) {
                    label = "0";
                } else {
                    label = "返回";
                }
                Button b = tvAsButton(label, idx >= 9 ? 14 : 20);
                final String lab = label;
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onKeypad(lab);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(78), dp(52));
                lp.setMargins(dp(6), dp(5), dp(6), dp(5));
                r.addView(b, lp);
            }
            pad.addView(r);
        }
        return pad;
    }

    private void onKeypad(String label) {
        if ("清除".equals(label)) {
            pinBuf.setLength(0);
            pinHint.setText("");
        } else if ("返回".equals(label)) {
            showPage(quizPage);
        } else {
            if (pinBuf.length() < 4) pinBuf.append(label);
        }
        pinDots.setText(dots(pinBuf.length()));
        if (pinBuf.length() == 4) checkPin();
    }

    private String dots(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('●');
        for (int i = n; i < 4; i++) sb.append('○');
        return sb.toString();
    }

    private void checkPin() {
        if (pinFails >= 3) {
            pinBuf.setLength(0);
            pinHint.setTextColor(0xFFFF6B6B);
            pinHint.setText("错误次数过多，请稍后再试");
            pinDots.setText(dots(0));
            return;
        }
        if (pinBuf.toString().equals(Prefs.pin(ctx))) {
            cancelled = true;
            ui.removeCallbacksAndMessages(null);
            pinHint.setTextColor(0xFF37D67A);
            pinHint.setText("已暂停，去忙吧");
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    listener.onParentUnlock();
                }
            }, 600);
        } else {
            pinFails++;
            pinBuf.setLength(0);
            pinHint.setTextColor(0xFFFF6B6B);
            pinHint.setText(pinFails >= 3
                    ? "错误次数过多，请稍后再试"
                    : "密码错误，请重试（" + (3 - pinFails) + " 次机会）");
            pinDots.setText(dots(0));
            if (pinFails >= 3) pinPage.setAlpha(0.55f);
        }
    }

    private void showPage(View target) {
        quizPage.setVisibility(target == quizPage ? View.VISIBLE : View.GONE);
        pinPage.setVisibility(target == pinPage ? View.VISIBLE : View.GONE);
        if (parentEntry != null) {
            parentEntry.setVisibility(target == quizPage ? View.VISIBLE : View.GONE);
        }
        if (target == pinPage) {
            pinBuf.setLength(0);
            pinDots.setText(dots(0));
            pinHint.setText("");
            pinPage.requestFocus();
        } else {
            focusFirst();
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 遥控器：拦掉返回键、菜单键，避免孩子退回桌面。 */
    static class BlockingFrame extends FrameLayout {
        BlockingFrame(Context c) {
            super(c);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_BACK
                    || k == KeyEvent.KEYCODE_ESCAPE
                    || k == KeyEvent.KEYCODE_MENU
                    || k == KeyEvent.KEYCODE_HOME) {
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button tvAsButton(String s, int sp) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setAllCaps(false);
        b.setTextColor(0xFFE8EEF7);
        b.setBackground(optionBackground());
        b.setFocusable(true);
        b.setPadding(dp(18), 0, dp(18), 0);
        return b;
    }

    private StateListDrawable optionBackground() {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, round(0xFF2F80ED, 0xFF8CC0FF));
        d.addState(new int[]{android.R.attr.state_pressed}, round(0xFF2563B8, 0xFF8CC0FF));
        d.addState(new int[]{-android.R.attr.state_enabled}, round(0xFF12161D, 0xFF222A36));
        d.addState(new int[]{}, round(0xFF161B24, 0xFF2B3546));
        return d;
    }

    private GradientDrawable round(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(2), stroke);
        return g;
    }

    private int dp(int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
