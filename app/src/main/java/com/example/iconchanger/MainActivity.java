package com.example.iconchanger;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PICK_IMAGE = 1001;

    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> shownApps = new ArrayList<>();
    private AppInfo pendingApp = null;
    private Bitmap pendingBitmap = null;
    private ListView listView;
    private TextView emptyView;
    private ArrayAdapter<AppInfo> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty);
        EditText search = findViewById(R.id.search);

        loadApps();

        adapter = new ArrayAdapter<AppInfo>(this, R.layout.row_app, shownApps) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.row_app, parent, false);
                }
                AppInfo a = getItem(position);
                ImageView iv = convertView.findViewById(R.id.icon);
                TextView label = convertView.findViewById(R.id.label);
                TextView pkg = convertView.findViewById(R.id.pkg);
                iv.setImageDrawable(a.icon);
                label.setText(a.label);
                pkg.setText(a.packageName);
                return convertView;
            }
        };
        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            pendingApp = shownApps.get(position);
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(pick, "选择图标图片"), REQ_PICK_IMAGE);
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        filter("");
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
            allApps.add(new AppInfo(pkg, label, pm.getApplicationIcon(ai)));
        }
        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private void filter(String q) {
        shownApps.clear();
        String needle = q.trim().toLowerCase();
        for (AppInfo a : allApps) {
            if (needle.isEmpty()
                    || a.label.toLowerCase().contains(needle)
                    || a.packageName.toLowerCase().contains(needle)) {
                shownApps.add(a);
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE || resultCode != Activity.RESULT_OK || data == null || pendingApp == null) {
            pendingApp = null;
            pendingBitmap = null;
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            pendingApp = null;
            pendingBitmap = null;
            return;
        }
        Bitmap bmp = decodeSampled(uri, 192, 192);
        if (bmp == null) {
            showPermissionDialog();
            pendingApp = null;
            pendingBitmap = null;
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

    private void showNameDialog(AppInfo app) {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_name, null);
        EditText input = view.findViewById(R.id.nameInput);
        input.setText(app.label);
        input.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .create();
        view.findViewById(R.id.nameInput);
        // positive/negative via custom buttons? use dialog buttons
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "创建", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = app.label;
            createShortcut(app, pendingBitmap, name);
            pendingApp = null;
            pendingBitmap = null;
        });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (d, w) -> {
            pendingApp = null;
            pendingBitmap = null;
        });
        dialog.setOnCancelListener(d -> {
            pendingApp = null;
            pendingBitmap = null;
        });
        dialog.show();
    }

    private void createShortcut(AppInfo app, Bitmap iconBmp, String name) {
        if (name == null || name.trim().isEmpty()) name = app.label;
        name = name.trim();
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
        if (launchIntent == null) {
            showPermissionDialog();
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
                if (!ok) showPermissionDialog();
                return;
            }
        }

        Intent add = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
        add.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        add.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);
        add.putExtra("duplicate", false);
        add.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBmp);
        sendBroadcast(add);
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("权限或启动器限制")
                .setMessage("无法创建快捷方式或读取图片。请在系统设置中为本应用授予必要权限后重试。")
                .setPositiveButton("去设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
