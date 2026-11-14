package com.family.studyguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** 兜底方案：没有悬浮窗权限时，用全屏 Activity 出题。 */
public class QuizActivity extends Activity implements QuizScreen.Listener {

    private MediaPauser pauser;

    public static void launch(Context c) {
        Intent i = new Intent(c, QuizActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        pauser = new MediaPauser(this);
        pauser.pause();

        QuizScreen screen = new QuizScreen(this, QuestionBank.pick(this, 8), this);
        View v = screen.createView();
        setContentView(v);
        v.requestFocus();
        screen.focusFirst();
    }

    @Override
    public void onBackPressed() {
        // 禁止返回，必须答题
    }

    @Override
    public void onSolved() {
        Prefs.bumpSolved(this);
        finish();
    }

    @Override
    public void onParentUnlock() {
        Prefs.setSuspendUntil(this, System.currentTimeMillis() + 30 * 60 * 1000L);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (pauser != null) pauser.resume();
        super.onDestroy();
    }
}
