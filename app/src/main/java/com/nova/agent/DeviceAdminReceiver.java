package com.nova.agent;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "Nova Admin Perangkat Aktif!", Toast.LENGTH_SHORT).show();
    }
}
