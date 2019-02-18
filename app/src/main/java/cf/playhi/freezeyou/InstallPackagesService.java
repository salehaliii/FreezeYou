package cf.playhi.freezeyou;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static cf.playhi.freezeyou.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.ApplicationLabelUtils.getApplicationLabel;
import static cf.playhi.freezeyou.ProcessUtils.destroyProcess;
import static cf.playhi.freezeyou.ToastUtils.showToast;

//Install and uninstall
public class InstallPackagesService extends Service {

    ArrayList<Intent> intentArrayList = new ArrayList<>();
    boolean processing = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        intent.putExtra("requestTime", new Date().getTime());
        if (processing) {
            intentArrayList.add(intent);
        } else {
            installAndUninstall(intent);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder mBuilder = new Notification.Builder(this);
            mBuilder.setSmallIcon(R.drawable.ic_notification);
            mBuilder.setContentText("安装与卸载");
            mBuilder.setContentTitle("安装与卸载");
            NotificationChannel channel = new NotificationChannel("InstallPackages", "安装与卸载", NotificationManager.IMPORTANCE_NONE);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null)
                notificationManager.createNotificationChannel(channel);
            mBuilder.setChannelId("InstallPackages");
            startForeground(5, mBuilder.build());
        } else {
            Notification.Builder mBuilder = new Notification.Builder(this);
            mBuilder.setSmallIcon(R.drawable.ic_notification);
            mBuilder.setContentTitle("安装与卸载");
            mBuilder.setContentText("安装与卸载");
            startForeground(5, mBuilder.getNotification());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void installAndUninstall(Intent intent) {
        processing = true;

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ?
                new Notification.Builder(this, "InstallPackages") :
                new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (intent.getBooleanExtra("install", true)) {//Install
            install(intent, builder, notificationManager);
        } else {//Uninstall
            uninstall(intent, builder, notificationManager);
        }

        //移除已完成的
        intentArrayList.remove(intent);
        if (intentArrayList.isEmpty()) {
            processing = false;
            stopSelf();
        } else {
            installAndUninstall(intentArrayList.get(0));
        }
    }

    private void uninstall(Intent intent, Notification.Builder builder, NotificationManager notificationManager) {
        try {
            Uri packageUri = intent.getParcelableExtra("packageUri");
            String packageName = packageUri.getEncodedSchemeSpecificPart();
            if (packageName == null) {
                showToast(this, "Invalid package name in URI: " + packageUri);
                return;
            }

            String willBeUninstalledName = getApplicationLabel(this, null, null, packageName);
            Drawable willBeUninstalledIcon = getApplicationIcon(this, packageName, null, false);

            builder.setContentTitle("正在卸载 " + willBeUninstalledName);
            builder.setLargeIcon(getBitmapFromDrawable(willBeUninstalledIcon));
            notificationManager.notify(packageName.hashCode(), builder.getNotification());

            if (Build.VERSION.SDK_INT >= 21) {
                getPackageManager().getPackageInstaller().uninstall(packageName,
                        PendingIntent.getBroadcast(this, packageName.hashCode(),
                                new Intent(
                                        this,
                                        InstallPackagesFinishedReceiver.class)
                                        .putExtra("name", willBeUninstalledName)
                                        .putExtra("pkgName", packageName)
                                        .putExtra("install", false), FLAG_UPDATE_CURRENT)
                                .getIntentSender());
            } else {
                // Root Mode
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
                outputStream.writeBytes("pm uninstall -k \"" + packageName + "\"\n");
                outputStream.writeBytes("exit\n");
                outputStream.flush();
                process.waitFor();
                destroyProcess(outputStream, process);
                builder.setContentTitle(willBeUninstalledName + " 卸载完成");
                notificationManager.notify(packageName.hashCode(), builder.getNotification());
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast(this, "Error uninstalling apk from content URI:" + e);
        }
    }

    private void install(Intent intent, Notification.Builder builder, NotificationManager notificationManager) {
        try {
            String apkFilePath = intent.getStringExtra("apkFilePath");
            if (apkFilePath == null || "".equals(apkFilePath) || !new File(apkFilePath).exists()) {
                Uri packageUri = intent.getParcelableExtra("packageUri");
                InputStream in = getContentResolver().openInputStream(packageUri);
                if (in == null) {
                    return;
                }

                String apkFileName = "package" + new Date().getTime() + "F.apk";
                apkFilePath = getExternalCacheDir() + File.separator + apkFileName;

                OutputStream out = new FileOutputStream(apkFilePath);
                byte[] buffer = new byte[1024 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
                out.close();
                in.close();
            }

            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkFilePath, 0);
            String willBeInstalledPackageName = packageInfo.packageName;
            packageInfo.applicationInfo.sourceDir = apkFilePath;
            packageInfo.applicationInfo.publicSourceDir = apkFilePath;
            String willBeInstalledName = pm.getApplicationLabel(packageInfo.applicationInfo).toString();
            Drawable willBeInstalledIcon = pm.getApplicationIcon(packageInfo.applicationInfo);

            builder.setContentTitle("正在安装 " + willBeInstalledName);
            builder.setLargeIcon(getBitmapFromDrawable(willBeInstalledIcon));
            notificationManager.notify(willBeInstalledPackageName.hashCode(), builder.getNotification());

            if (Build.VERSION.SDK_INT >= 21) {// && isDeviceOwner(this)
                PackageInstaller packageInstaller = pm.getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(willBeInstalledPackageName);
                int sessionId = packageInstaller.createSession(params);
                PackageInstaller.Session session = packageInstaller.openSession(sessionId);
                OutputStream outputStream = session.openWrite(
                        Integer.toString(apkFilePath.hashCode()), 0, -1);
                InputStream in1 = new FileInputStream(apkFilePath);
                byte[] buffer = new byte[1024 * 1024];
                int bytesRead;
                while ((bytesRead = in1.read(buffer)) >= 0) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                session.fsync(outputStream);
                outputStream.close();
                in1.close();
                session.commit(
                        PendingIntent.getBroadcast(this, sessionId,
                                new Intent(
                                        this,
                                        InstallPackagesFinishedReceiver.class)
                                        .putExtra("name", willBeInstalledName)
                                        .putExtra("pkgName", willBeInstalledPackageName)
                                        .putExtra("apkFilePath", apkFilePath), FLAG_UPDATE_CURRENT)
                                .getIntentSender());
            } else {
                // Root Mode
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream outputStream = new DataOutputStream(process.getOutputStream());
                outputStream.writeBytes("pm install -r \"" + apkFilePath + "\"\n");
                outputStream.writeBytes("exit\n");
                outputStream.flush();
                process.waitFor();
                destroyProcess(outputStream, process);
                // Delete Temp File
                File file = new File(apkFilePath);
                if (file.exists()) {
                    if (!file.delete())
                        if (!file.delete())
                            showToast(this, "Cannot delete temp file: " + apkFilePath);
                }
                builder.setContentTitle(willBeInstalledName + " 安装完成");
                notificationManager.notify(willBeInstalledPackageName.hashCode(), builder.getNotification());
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToast(this, "Error installing apk from content URI:" + e);
        }

    }
}