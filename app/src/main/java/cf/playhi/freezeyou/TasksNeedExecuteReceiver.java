package cf.playhi.freezeyou;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.NonNull;

public class TasksNeedExecuteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra("id", -5);
        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);
        String task = intent.getStringExtra("task");
        String repeat = intent.getStringExtra("repeat");
        SQLiteDatabase db = context.openOrCreateDatabase("scheduledTasks", Context.MODE_PRIVATE, null);
        if ("0".equals(repeat) && id != -5) {
            db.execSQL("UPDATE tasks SET enabled = 0 WHERE _id = " + Integer.toString(id) + ";");
        } else {
            Support.publishTask(context, id, hour, minute, repeat, task);
        }
        if (task != null && !"".equals(task)) {
            Support.runTask(task.toLowerCase(), context);//全部转小写
        }
    }
}
