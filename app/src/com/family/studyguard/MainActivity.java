package com.family.studyguard;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 家长设置页：改间隔、改密码、开关守卫、立即测试。 */
public class MainActivity extends Activity {

    static volatile boolean isForeground = false;

    private EditText etInterval;
    private EditText etPin;
    private EditText etRemote;
    private EditText etExplain;
    private Switch swEnable;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildRoot());
        loadValues();
        handleCmd(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleCmd(intent);
    }

    /** 支持 adb 脚本触发：am start -n com.family.studyguard/.MainActivity -e cmd test */
    private void handleCmd(Intent i) {
        if (i == null) return;
        String cmd = i.getStringExtra("cmd");
        if (cmd == null) return;
        if ("test".equals(cmd)) {
            GuardService.testNow(this);
        } else if ("stop".equals(cmd)) {
            Prefs.setEnabled(this, false);
            swEnable.setChecked(false);
            GuardService.stop(this);
            refreshStatus();
        } else if ("set".equals(cmd)) {
            int ev = i.getIntExtra("explain", -1);
            if (ev < 0) {
                try {
                    ev = Integer.parseInt(String.valueOf(i.getStringExtra("explain")));
                } catch (Exception ignored) {
                    ev = -1;
                }
            }
            if (ev >= 0) Prefs.setExplainMin(this, ev);
            int iv = i.getIntExtra("interval", 0);
            if (iv <= 0) {
                try {
                    iv = Integer.parseInt(String.valueOf(i.getStringExtra("interval")));
                } catch (Exception ignored) {
                    iv = 0;
                }
            }
            String pin = i.getStringExtra("pin");
            if (iv > 0) {
                Prefs.setIntervalMin(this, iv);
                etInterval.setText(String.valueOf(Prefs.intervalMin(this)));
            }
            if (pin != null && pin.length() == 4) {
                Prefs.setPin(this, pin);
                etPin.setText(pin);
            }
            Prefs.setEnabled(this, true);
            swEnable.setChecked(true);
            GuardService.start(this);
            refreshStatus();
        } else if ("remote-clear".equals(cmd)) {
            Prefs.setRemoteUrl(this, "");
            if (etRemote != null) etRemote.setText("");
            RemoteBank.clear(this);
            refreshStatus();
        } else if ("remote".equals(cmd)) {
            String url = i.getStringExtra("url");
            if (url != null) {
                Prefs.setRemoteUrl(this, url);
                etRemote.setText(Prefs.remoteUrl(this));
            }
            if (Prefs.remoteUrl(this).isEmpty()) {
                RemoteBank.clear(this);
                refreshStatus();
                return;
            }
            RemoteBank.sync(this, Prefs.remoteUrl(this), new RemoteBank.Callback() {
                @Override
                public void onResult(boolean ok, int count, String msg) {
                    toast(msg);
                    refreshStatus();
                }
            });
        } else if ("resume".equals(cmd)) {
            Prefs.setSuspendUntil(this, 0L);
            refreshStatus();
        } else if ("start".equals(cmd)) {
            Prefs.setEnabled(this, true);
            swEnable.setChecked(true);
            GuardService.start(this);
            refreshStatus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        refreshStatus();
    }

    @Override
    protected void onPause() {
        isForeground = false;
        super.onPause();
    }

    private View buildRoot() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(64), dp(24), dp(64), dp(20));
        col.setBackgroundColor(0xFF0F1218);

        TextView title = text("学习守卫 · 电视答题锁", 30, 0xFFFFFFFF, true);
        col.addView(title);

        TextView sub = text("每 N 分钟全屏弹出一道初中语数英题目，答对才能继续看。", 16, 0xFF8A94A6, false);
        sub.setPadding(0, dp(6), 0, dp(16));
        col.addView(sub);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setPadding(0, dp(4), 0, dp(4));
        timeRow.addView(text("出题间隔 / 解析停留（分钟）", 22, 0xFFE8EEF7, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        etInterval = edit("1", InputType.TYPE_CLASS_NUMBER);
        etInterval.setHint("间隔");
        etExplain = edit("1", InputType.TYPE_CLASS_NUMBER);
        etExplain.setHint("解析");
        LinearLayout.LayoutParams f1 = new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams f2 = new LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT);
        f2.leftMargin = dp(20);
        timeRow.addView(etInterval, f1);
        timeRow.addView(etExplain, f2);
        col.addView(timeRow);
        col.addView(formRow("家长密码（4 位）", etPin = edit("1234", InputType.TYPE_CLASS_NUMBER)));
        col.addView(formRow("在线题库地址（留空=不用）", etRemote = edit("", InputType.TYPE_CLASS_TEXT)));

        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        switchRow.setPadding(0, dp(6), 0, dp(12));
        TextView swLabel = text("启用学习守卫", 22, 0xFFE8EEF7, false);
        switchRow.addView(swLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        swEnable = new Switch(this);
        swEnable.setChecked(true);
        switchRow.addView(swEnable);
        col.addView(switchRow);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button("保存并启动");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        Button test = button("立即出题测试");
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GuardService.testNow(MainActivity.this);
                toast("已触发，稍等 1 秒");
            }
        });
        Button syncBtn = button("同步题库");
        syncBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = etRemote.getText().toString().trim();
                Prefs.setRemoteUrl(MainActivity.this, url);
                if (url.isEmpty()) {
                    RemoteBank.clear(MainActivity.this);
                    toast("已关闭在线题库，回到内置题库");
                    refreshStatus();
                    return;
                }
                toast("开始同步…");
                RemoteBank.sync(MainActivity.this, url, new RemoteBank.Callback() {
                    @Override
                    public void onResult(boolean ok, int count, String msg) {
                        toast(msg);
                        refreshStatus();
                    }
                });
            }
        });
        Button stop = button("停止守卫");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setEnabled(MainActivity.this, false);
                swEnable.setChecked(false);
                GuardService.stop(MainActivity.this);
                refreshStatus();
                toast("已停止");
            }
        });
        btns.addView(save);
        btns.addView(test);
        btns.addView(syncBtn);
        btns.addView(stop);
        col.addView(btns);

        Button perm = button("授予“悬浮窗”权限（关键）");
        perm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlaySettings();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(14);
        col.addView(perm, plp);

        tvStatus = text("", 15, 0xFF6B7686, false);
        tvStatus.setPadding(0, dp(16), 0, 0);
        col.addView(tvStatus);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0F1218);
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private LinearLayout formRow(String label, EditText input) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView l = text(label, 22, 0xFFE8EEF7, false);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(input);
        return row;
    }

    private EditText edit(String value, int inputType) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(inputType);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        e.setGravity(Gravity.CENTER);
        e.setMinWidth(dp(180));
        return e;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        lp.rightMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private void loadValues() {
        etInterval.setText(String.valueOf(Prefs.intervalMin(this)));
        etPin.setText(Prefs.pin(this));
        etExplain.setText(String.valueOf(Prefs.explainMin(this)));
        etRemote.setText(Prefs.remoteUrl(this));
        swEnable.setChecked(Prefs.enabled(this));
    }

    private void save() {
        int min;
        try {
            min = Integer.parseInt(etInterval.getText().toString().trim());
        } catch (Exception e) {
            toast("间隔请填数字");
            return;
        }
        if (min < 1 || min > 120) {
            toast("间隔请在 1~120 分钟之间");
            return;
        }
        String pin = etPin.getText().toString().trim();
        if (pin.length() != 4) {
            toast("家长密码请填 4 位数字");
            return;
        }

        int explain;
        try {
            explain = Integer.parseInt(etExplain.getText().toString().trim());
        } catch (Exception e) {
            toast("解析停留请填数字（0~10 分钟）");
            return;
        }
        if (explain < 0 || explain > 10) {
            toast("解析停留请在 0~10 分钟之间");
            return;
        }
        Prefs.setExplainMin(this, explain);

        Prefs.setRemoteUrl(this, etRemote.getText().toString());
        Prefs.setIntervalMin(this, min);
        Prefs.setPin(this, pin);
        Prefs.setEnabled(this, swEnable.isChecked());

        if (swEnable.isChecked()) {
            GuardService.start(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                toast("已启动，但还需授予悬浮窗权限");
                openOverlaySettings();
            } else {
                toast("已保存，守卫运行中");
            }
        } else {
            GuardService.stop(this);
            toast("已保存，守卫已关闭");
        }
        refreshStatus();
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable t) {
                toast("请在系统设置 → 应用权限 中允许“显示在其他应用上层”");
            }
        } else {
            toast("当前系统版本无需单独授权");
        }
    }

    private void refreshStatus() {
        boolean overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        StringBuilder sb = new StringBuilder();
        sb.append("状态：").append(Prefs.enabled(this) ? "已启用" : "已关闭");
        sb.append("　间隔：").append(Prefs.intervalMin(this)).append(" 分钟");
        sb.append("　解析停留：").append(Prefs.explainMin(this)).append(" 分钟");
        sb.append("\n悬浮窗权限：").append(overlayOk ? "已授予 ✓" : "未授予 ✗（必须授予才能拦截画面）");
        sb.append("\n题库：").append(QuestionBank.size(this)).append(" 题（语文 ")
                .append(QuestionBank.countOf(this, "语文")).append(" / 数学 ")
                .append(QuestionBank.countOf(this, "数学")).append(" / 英语 ")
                .append(QuestionBank.countOf(this, "英语")).append("）");
        sb.append("\n累计答对：").append(Prefs.solvedCount(this)).append(" 题");
        String remoteUrl = Prefs.remoteUrl(this);
        if (remoteUrl.isEmpty()) {
            sb.append("\n在线题库：未配置（用内置 ").append(QuestionBank.size(this)).append(" 题）");
        } else {
            sb.append("\n在线题库：").append(QuestionBank.isUsingRemote() ? "使用中 " : "未生效 ")
                    .append(Prefs.remoteCount(this)).append(" 题");
        }
        long until = Prefs.suspendUntil(this);
        if (until > System.currentTimeMillis()) {
            long min = (until - System.currentTimeMillis()) / 60000L;
            sb.append("\n家长放行剩余：").append(min).append(" 分钟");
        }
        tvStatus.setText(sb.toString());
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
