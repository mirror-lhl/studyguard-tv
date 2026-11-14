package com.family.studyguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 电视开机后自动拉起守卫服务，避免孩子重启电视绕过。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.enabled(context)) return;
        GuardService.start(context);
    }
}
