package org.bdj;

import java.awt.Color;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Encapsulates the capabilities of the screen.
 */
public class Screen extends Container {
    private static final long serialVersionUID = 0x4141414141414141L;

    /** Message types for color coding */
    public static class MessageType {
        public static final MessageType INFO = new MessageType("INFO", Color.white);
        public static final MessageType SUCCESS = new MessageType("SUCCESS", Color.green);
        public static final MessageType ERROR = new MessageType("ERROR", Color.red);
        public static final MessageType WARNING = new MessageType("WARNING", Color.yellow);

        private final String name;
        private final Color color;

        private MessageType(String name, Color color) {
            this.name = name;
            this.color = color;
        }

        public Color getColor() {
            return color;
        }

        public String toString() {
            return name;
        }
    }

    /** Simple container for a message and its type */
    private static class Message {
        final String text;
        final MessageType type;

        Message(String text, MessageType type) {
            this.text = text;
            this.type = type;
        }
    }

    private final Font FONT = new Font("SansSerif", Font.BOLD, 28);
    private final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 48);
    private final Font PROGRESS_FONT = new Font("SansSerif", Font.BOLD, 28);

    private final ArrayList messages = new ArrayList();
    private int progressPercent = 0;
    private String progressMessage = "";
    private String title = "PS5 BD-JB Autoloader " + Version.VERSION;

    private static final Screen instance = new Screen();

    private volatile boolean isPainting = false;
    private volatile boolean isDirty = false;
    private volatile boolean isVisible = true;

    /**
     * Default constructor. Declared as private since this class is singleton.
     */
    private Screen() {
        super();
        setBackground(new Color(0x272727));
        setForeground(Color.WHITE);
        
        // Add component listener to track visibility
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                isVisible = true;
                safeRepaint();
            }
            
            public void componentHidden(ComponentEvent e) {
                isVisible = false;
            }
        });
    }

    /**
     * Retrieves the singleton instance of the screen.
     *
     * @return {@code Screen} instance, there is only one in the application.
     */
    public static Screen getInstance() {
        return instance;
    }

    /**
     * Adds a message on the singleton screen instance, immediately repainting it.
     *
     * @param msg Message to add.
     */
    public static void println(String msg) {
        println(msg, MessageType.INFO, true, false);
    }

    /**
     * Adds a message on the singleton screen instance, with control on whether to immediately repaint it or not.
     *
     * @param msg Message to add.
     * @param type Type of the message for color coding.
     * @param repaint Whether to repaint the screen right away or not.
     * @param replaceLast Whether to add a new line or replace the last printed line (useful for progress output).
     */
    public static void println(String msg, MessageType type, boolean repaint, boolean replaceLast) {
        getInstance().print(msg, type, repaint, replaceLast);
    }

    /**
     * Adds a message to this screen instance, immediately repainting it.
     *
     * @param msg Message to add.
     * @param repaint Whether to repaint the screen right away or not.
     * @param replaceLast Whether to add a new line or replace the last printed line.
     */
    public void print(String msg, boolean repaint, boolean replaceLast) {
        print(msg, MessageType.INFO, repaint, replaceLast);
    }

    /**
     * Adds a message to this screen instance with a specific type.
     *
     * @param msg Message to add.
     * @param type Message type for color coding.
     * @param repaint Whether to repaint the screen right away or not.
     * @param replaceLast Whether to add a new line or replace the last printed line.
     */
    public void print(String msg, MessageType type, boolean repaint, boolean replaceLast) {
        if (msg == null) {
            msg = "null";
        }

        synchronized (this) {
            if (replaceLast && messages.size() > 0) {
                messages.remove(messages.size() - 1);
            }
            messages.add(new Message(msg, type));
            if (messages.size() > 16) {
                messages.remove(0);
            }
            isDirty = true;
        }

        if (repaint) {
            safeRepaint();
        }
    }

    /**
     * Sets the current progress and label.
     *
     * @param percent Progress percentage (0-100).
     * @param label Progress label message.
     */
    public void setProgress(int percent, String label) {
        synchronized (this) {
            this.progressPercent = Math.max(0, Math.min(100, percent));
            this.progressMessage = label != null ? label : "";
            isDirty = true;
        }
        safeRepaint();
    }

    /**
     * Sets the UI title.
     *
     * @param title New title.
     */
    public void setTitle(String title) {
        synchronized (this) {
            this.title = title != null ? title : "";
            isDirty = true;
        }
        safeRepaint();
    }

    private void safeRepaint() {
        if (EventQueue.isDispatchThread()) {
            // Already on EDT, repaint directly
            if (isDisplayable()) {
                repaint();
            }
        } else {
            // Not on EDT, queue the repaint
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    if (isDisplayable()) {
                        repaint();
                    }
                }
            });
        }
    }

    public void update(Graphics g) {
        paint(g);
    }

    /**
     * Prints the exception's stack trace on this screen instance.
     *
     * @param e Exception whose stack trace to print.
     */
    public void printStackTrace(Throwable e) {
        if (e == null) {
            print("null exception", MessageType.ERROR, true, false);
            return;
        }

        StringTokenizer st;
        StringBuffer sb;

        try {
            StringWriter sw = new StringWriter();
            try {
                PrintWriter pw = new PrintWriter(sw);
                try {
                    e.printStackTrace(pw);
                } finally {
                    pw.close();
                }

                String stackTrace = sw.toString();
                st = new StringTokenizer(stackTrace, "\n", false);
                sb = new StringBuffer(stackTrace.length());
            } finally {
                sw.close();
            }

            synchronized (this) {
                while (st.hasMoreTokens()) {
                    String line = st.nextToken();
                    sb.setLength(0);
                    for (int i = 0; i < line.length(); ++i) {
                        char c = line.charAt(i);
                        if (c == '\t') {
                            sb.append("   ");
                        } else if (c == '\r') {
                            continue;
                        } else {
                            sb.append(c);
                        }
                    }
                    print(sb.toString(), MessageType.ERROR, !st.hasMoreTokens(), false);
                }
            }
        } catch (IOException ioEx) {
            printThrowable(e);

            throw new RuntimeException("Another exception occurred while printing stacktrace. " + ioEx.getClass().getName() + ": " + ioEx.getMessage());
        }
    }

    /**
     * Convenience method to print basic information about an exception, without printing all the stack trace.
     *
     * @param e Exception to print.
     */
    public void printThrowable(Throwable e) {
        if (e == null) {
            print("null throwable", MessageType.ERROR, true, false);
            return;
        }
        print(e.getClass().getName() + ": " + e.getMessage(), MessageType.ERROR, true, false);
    }

    /**
     * Repaint the screen.
     *
     * @param g {@code} Graphics code on which the screen data is painted.
     */
    public void paint(Graphics g) {
        if (g == null) {
            return;
        }

        List messagesCopy;
        int pct;
        String pctMsg;
        String currentTitle;

        synchronized (this) {
            if (isPainting || !isDirty) return;
            isPainting = true;
            isDirty = false;

            messagesCopy = new ArrayList(messages);
            pct = this.progressPercent;
            pctMsg = this.progressMessage;
            currentTitle = this.title;
        }

        try {
            int width = getWidth();
            int height = getHeight();

            // 1. Draw Background
            g.setColor(getBackground());
            g.fillRect(0, 0, width, height);

            // 2. Draw Title
            g.setFont(TITLE_FONT);
            g.setColor(Color.white);
            int titleWidth = g.getFontMetrics().stringWidth(currentTitle);
            g.drawString(currentTitle, (width - titleWidth) / 2, 80);

            // 3. Draw Log Container
            int logWidth = (int) (width * 0.7);
            int logHeight = (int) (height * 0.70);
            int logX = (width - logWidth) / 2;
            int logY = 150;

            // Container Background
            g.setColor(Color.black);
            g.fillRect(logX, logY, logWidth, logHeight);

            // Container Border (Blue Accent)
            g.setColor(new Color(0x0036AA));
            g.drawRect(logX - 1, logY - 1, logWidth + 1, logHeight + 1);
            g.drawRect(logX - 2, logY - 2, logWidth + 3, logHeight + 3);

            // Render Messages
            g.setFont(FONT);
            int fontHeight = g.getFontMetrics().getHeight();
            int msgX = logX + 15;
            int msgY = logY + 40;
            for (int i = 0; i < messagesCopy.size(); i++) {
                Message msg = (Message) messagesCopy.get(i);
                g.setColor(msg.type.getColor());
                g.drawString("> " + msg.text, msgX, msgY);
                msgY += fontHeight + 8;
            }

            // 4. Draw Progress Bar
            int pbWidth = (int) (width * 0.6);
            int pbHeight = 40;
            int pbX = (width - pbWidth) / 2;
            int pbY = logY + logHeight + 30;

            drawProgressBar(g, pbX, pbY, pbWidth, pbHeight, pct, pctMsg);
        } finally {
            synchronized (this) {
                isPainting = false;
            }
        }
    }

    /**
     * Helper to draw a styled progress bar.
     */
    private void drawProgressBar(Graphics g, int x, int y, int width, int height, int percent, String label) {
        // Background
        g.setColor(new Color(0x202020));
        g.fillRoundRect(x, y, width, height, 16, 16);

        // Fill (Blue Accent)
        if (percent > 0) {
            g.setColor(new Color(0x0036AA));
            int fillWidth = (int) (width * (percent / 100.0));
            g.fillRoundRect(x, y, fillWidth, height, 16, 16);
        }

        // Percentage Text
        g.setFont(PROGRESS_FONT);
        g.setColor(Color.white);
        String pctStr = percent + "%";
        int pctWidth = g.getFontMetrics().stringWidth(pctStr);
        g.drawString(pctStr, x + width + 15, y + (height / 2) + 7);

        // Progress Label
        if (label != null && label.length() > 0) {
            int labelWidth = g.getFontMetrics().stringWidth(label);
            g.drawString(label, (getWidth() - labelWidth) / 2, y + height + 25);
        }
    }

    public static void clear() {
        getInstance().clearMessages();
    }
    
    public void clearMessages() {
        synchronized (this) {
            messages.clear();
            isDirty = true;
        }
        safeRepaint();
    }
}