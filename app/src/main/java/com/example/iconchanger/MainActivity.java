package com.example.iconchanger;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PICK_IMAGE = 1001;

    private final List<AppInfo> apps = new ArrayList<>();
    private AppInfo pendingApp = null;
    private Bitmap pendingBitmap = null;
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.list);
        loadApps();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            pendingApp = apps.get(position);
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(pick, "选择图标图片"), REQ_PICK_IMAGE);
        });
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo ri : infos) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            String pkg = ai.packageName;
            String label = pm.getApplicationLabel(ai).toString();
            apps.add(new AppInfo(pkg, label, pm.getApplicationIcon(ai)));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));

        ArrayAdapter<AppInfo> adapter = new ArrayAdapter<AppInfo>(this, R.layout.row_app, apps) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.row_app, parent, false);
                }
                AppInfo a = getItem(position);
                ImageView iv = convertView.findViewById(R.id.icon);
                TextView tv = convertView.findViewById(R.id.label);
                iv.setImageDrawable(a.icon);
                tv.setText(a.label + "\n" + a.packageName);
                return convertView;
            }
        };
        listView.setAdapter(adapter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE || resultCode != Activity.RESULT_OK || data == null || pendingApp == null) {
            pendingApp = null;
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            pendingApp = null;
            return;
        }
        Bitmap bmp = decodeSampled(uri, 192, 192);
        if (bmp == null) {
            Toast.makeText(this, "无法读取图片", Toast.LENGTH_SHORT).show();
            pendingApp = null;
            return;
        }
        pendingBitmap = bmp;
        showNameDialog(pendingApp);
    }

    private Bitmap decodeSampled(Uri uri, int reqW, int reqH) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            if (is != null) is.close();
            int scale = 1;
            while (opts.outWidth / scale / 2 >= reqW && opts.outHeight / scale / 2 >= reqH) {
                scale *= 2;
            }
            is = getContentResolver().openInputStream(uri);
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = scale;
            Bitmap b = BitmapFactory.decodeStream(is, null, opts);
            if (is != null) is.close();
            if (b == null) return null;
            return Bitmap.createScaledBitmap(b, reqW, reqH, true);
        } catch (Exception e) {
            return null;
        }
    }

    private void createShortcut(AppInfo app, Bitmap iconBmp, String name) {
        if (name == null || name.trim().isEmpty()) name = app.label;
        name = name.trim();
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
        if (launchIntent == null) {
            Toast.makeText(this, "无法获取启动 Intent", Toast.LENGTH_SHORT).show();
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
            if (sm != null && sm.isRequestPinShortcutSupported()) {
                android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(this, app.packageName + "_" + System.currentTimeMillis())
                        .setShortLabel(name)
                        .setLongLabel(name)
                        .setIcon(android.graphics.drawable.Icon.createWithBitmap(iconBmp))
                        .setIntent(launchIntent)
                        .build();
                boolean ok = sm.requestPinShortcut(info, null);
                Toast.makeText(this, ok ? "请在弹窗确认创建快捷方式" : "创建失败", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Fallback for older launchers
        Intent add = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        add.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        add.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
        add.putExtra("duplicate", false);
        add.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBmp);
        sendBroadcast(add);
        Toast.makeText(this, "已发送创建快捷方式请求", Toast.LENGTH_SHORT).show();
    }

    private void showNameDialog(AppInfo app) {
        EditText input = new EditText(this);
        input.setText(app.label);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("快捷方式名称")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = app.label;
                    createShortcut(app, pendingBitmap, name);
                    pendingApp = null;
                    pendingBitmap = null;
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    pendingApp = null;
                    pendingBitmap = null;
                })
                .show();
    }
}
