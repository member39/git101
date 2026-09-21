package com.vidrinks.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://www.freshme.ai.studio";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQ_BT = 1001;

    private WebView webView;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        requestBluetoothPermissionIfNeeded();

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new PrinterBridge(), "AndroidPrinter");
        webView.loadUrl(HOME_URL);

        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        Button printerButton = new Button(this);
        printerButton.setText("🖨 พิมพ์");
        printerButton.setAllCaps(false);
        printerButton.setOnClickListener(v -> showPairedPrinters());
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.BOTTOM);
        int margin = dp(16);
        buttonParams.setMargins(margin, margin, margin, margin);
        root.addView(printerButton, buttonParams);

        setContentView(root);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
        }
    }

    private boolean canUseBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "อุปกรณ์นี้ไม่มี Bluetooth", Toast.LENGTH_LONG).show();
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissionIfNeeded();
            return false;
        }
        return true;
    }

    private ArrayList<BluetoothDevice> pairedDevices() {
        ArrayList<BluetoothDevice> list = new ArrayList<>();
        if (!canUseBluetooth()) return list;
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        if (bonded != null) list.addAll(bonded);
        return list;
    }

    private void showPairedPrinters() {
        ArrayList<BluetoothDevice> devices = pairedDevices();
        if (devices.isEmpty()) {
            Toast.makeText(this, "ยังไม่พบอุปกรณ์ Bluetooth ที่จับคู่ไว้", Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice d = devices.get(i);
            String name = d.getName() == null ? "Bluetooth device" : d.getName();
            names[i] = name + "\n" + d.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("เลือกเครื่องพิมพ์")
                .setItems(names, (dialog, which) -> {
                    BluetoothDevice device = devices.get(which);
                    String test = "VI Drinks\nBluetooth printer test\n------------------------\n\n";
                    printToDevice(device.getAddress(), test);
                })
                .setNegativeButton("ยกเลิก", null)
                .show();
    }

    private void printToDevice(String macAddress, String text) {
        if (!canUseBluetooth()) return;
        Toast.makeText(this, "กำลังส่งไปเครื่องพิมพ์...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                socket.connect();
                OutputStream out = socket.getOutputStream();
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
                runOnUiThread(() -> Toast.makeText(this, "ส่งข้อมูลไปเครื่องพิมพ์แล้ว", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "พิมพ์ไม่สำเร็จ: " + message, Toast.LENGTH_LONG).show());
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (Exception ignored) { }
                }
            }
        }).start();
    }

    public class PrinterBridge {
        @JavascriptInterface
        public String getPairedPrinters() {
            JSONArray array = new JSONArray();
            try {
                for (BluetoothDevice device : pairedDevices()) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", device.getName());
                    obj.put("address", device.getAddress());
                    array.put(obj);
                }
            } catch (Exception ignored) { }
            return array.toString();
        }

        @JavascriptInterface
        public void printText(String macAddress, String text) {
            runOnUiThread(() -> printToDevice(macAddress, text == null ? "" : text));
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
