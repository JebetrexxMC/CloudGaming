package com.limelight.cloudsync;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs an .apk, .xapk or .apks file the user picked from phone storage, without the
 * Play Store. XAPK/APKS are zips of split APKs: every *.apk entry goes into one
 * PackageInstaller session. Nothing is bundled or downloaded by CloudSync itself.
 */
final class XapkInstaller {
    static final int PICK_REQUEST = 7301;
    private static final String ACTION = "com.limelight.cloudsync.INSTALL_STATUS";
    private static BroadcastReceiver receiver;

    private XapkInstaller() {}

    /** Opens the system file picker. Result comes back in onActivityResult(PICK_REQUEST). */
    static void pick(Activity a) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "Allow CloudSync to install apps, then tap again", Toast.LENGTH_LONG).show();
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive", "application/zip",
                "application/octet-stream", "application/xapk-package-archive"});
        a.startActivityForResult(i, PICK_REQUEST);
    }

    /** Installs the picked file on a background thread. */
    static void install(final Activity a, final Uri uri) {
        listen(a);
        toast(a, "Preparing install...");
        new Thread(() -> {
            PackageInstaller pi = a.getPackageManager().getPackageInstaller();
            int id = -1;
            try {
                PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                id = pi.createSession(p);
                PackageInstaller.Session s = pi.openSession(id);
                int apks = 0;
                boolean obb = false;
                try (InputStream in = a.getContentResolver().openInputStream(uri);
                     ZipInputStream zip = new ZipInputStream(in)) {
                    ZipEntry e;
                    boolean plainApk = false;
                    while ((e = zip.getNextEntry()) != null) {
                        String n = e.getName();
                        if (n.equals("AndroidManifest.xml") || n.equals("classes.dex")) plainApk = true;
                        if (n.toLowerCase().startsWith("android/obb/")) obb = true;
                        if (!e.isDirectory() && n.toLowerCase().endsWith(".apk")) {
                            String name = "split_" + apks + ".apk";
                            try (OutputStream out = s.openWrite(name, 0, e.getSize())) {
                                copy(zip, out);
                                s.fsync(out);
                            }
                            apks++;
                        }
                    }
                    if (apks == 0 && plainApk) {
                        // A normal single .apk: stream the whole file.
                        try (InputStream whole = a.getContentResolver().openInputStream(uri);
                             OutputStream out = s.openWrite("base.apk", 0, -1)) {
                            copy(whole, out);
                            s.fsync(out);
                        }
                        apks = 1;
                    }
                }
                if (apks == 0) { s.abandon(); toast(a, "No APK found in that file"); return; }
                if (obb) toast(a, "Note: this XAPK has OBB data; the game will download it on first start");
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent cb = PendingIntent.getBroadcast(a, id,
                        new Intent(ACTION).setPackage(a.getPackageName()), flags);
                s.commit(cb.getIntentSender());
                s.close();
                toast(a, "Installing " + apks + " APK part(s)...");
            } catch (Exception ex) {
                if (id != -1) try { pi.abandonSession(id); } catch (Exception ignored) {}
                toast(a, "Install failed: " + ex.getMessage());
            }
        }).start();
    }

    private static void listen(final Activity a) {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        c.startActivity(confirm);
                    }
                } else if (status == PackageInstaller.STATUS_SUCCESS) {
                    Toast.makeText(c, "Installed", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(c, "Install failed: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                            Toast.LENGTH_LONG).show();
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) a.getApplicationContext().registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else a.getApplicationContext().registerReceiver(receiver, f);
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[1 << 16];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
    }

    private static void toast(final Activity a, final String m) {
        a.runOnUiThread(() -> Toast.makeText(a, m, Toast.LENGTH_LONG).show());
    }
}
