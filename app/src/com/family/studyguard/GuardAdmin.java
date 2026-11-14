package com.family.studyguard;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 设备管理器。激活后，系统设置里的“卸载”会先被拦一道：
 * 必须先取消激活设备管理器才能卸载，取消时会弹出我们写的警告语。
 */
public class GuardAdmin extends DeviceAdminReceiver {

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "「学习守卫」正在保护这台电视。关闭后孩子可以自行卸载，无法再限制看电视。";
    }
}
