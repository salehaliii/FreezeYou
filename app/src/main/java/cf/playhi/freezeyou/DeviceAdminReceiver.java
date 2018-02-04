package cf.playhi.freezeyou;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {

    void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        showToast(context, "Enabled");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Why?";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        showToast(context, "Disabled");
    }

    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context.getApplicationContext(), DeviceAdminReceiver.class);
    }
}