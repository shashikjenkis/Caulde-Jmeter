package com.claudeplugin.jmeter.ui;

import com.claudeplugin.jmeter.service.ClaudeApiService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * TAB 5: JMeter AI Chat
 *
 * A conversational interface for asking any JMeter question.
 * Maintains full conversation history for multi-turn context.
 * Supports quick-fire suggestion chips for common questions.
 */
public class ChatTab extends JPanel {

    private final ClaudeApiService claudeService;
    private final Runnable checkApiKey;

    // Chat history sent to Claude (multi-turn context)
    private final List<String[]> conversationHistory = new ArrayList<>(); // [role, content]

    // UI
    private JTextPane chatPane;
    private StyledDocument chatDoc;
    private JTextField inputField;
    private JButton sendBtn;
    private JLabel statusLabel;

    // Styles
    private Style userStyle, claudeStyle, timeStyle, errorStyle, labelStyle;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public ChatTab(ClaudeApiService claudeService, Runnable checkApiKey) {
        this.claudeService = claudeService;
        this.checkApiKey   = checkApiKey;
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 0, 0, 0));

        add(buildChatArea(),    BorderLayout.CENTER);
        add(buildSuggestions(), BorderLayout.NORTH);
        add(buildInputRow(),    BorderLayout.SOUTH);

        initStyles();
        appendWelcome();
    }

    // ── Chat display area ─────────────────────────────────────────────────────

    private JScrollPane buildChatArea() {
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(Color.WHITE);
        chatPane.setBorder(new EmptyBorder(8, 8, 8, 8));
        chatDoc = chatPane.getStyledDocument();

        JScrollPane sp = new JScrollPane(chatPane);
        sp.setBorder(BorderFactory.createLineBorder(Color.decode("#E0E0E0"), 1));
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        return sp;
    }

    // ── Quick suggestion chips ────────────────────────────────────────────────

    private JPanel buildSuggestions() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        p.setOpaque(false);

        String[] suggestions = {
            "How do I add a JSON Extractor?",
            "What is a good ramp-up strategy for 1000 users?",
            "How do I handle dynamic CSRF tokens?",
            "Difference between Throughput and Response time?",
            "How to parameterize with CSV Data Set?",
            "Best assertions for REST API testing?",
            "How to run JMeter in non-GUI mode?",
            "How do I use Regular Expression Extractor?"
        };

        JLabel lbl = new JLabel("Quick questions:");
        lbl.setFont(lbl.getFont().deriveFont(11f));
        lbl.setForeground(Color.GRAY);
        p.add(lbl);

        for (String s : suggestions) {
            JButton chip = new JButton(s);
            chip.setFont(chip.getFont().deriveFont(11f));
            chip.setBackground(Color.decode("#F5F5F5"));
            chip.setBorderPainted(true);
            chip.setFocusPainted(false);
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chip.addActionListener(e -> sendMessage(s));
            p.add(chip);
        }
        return p;
    }

    // ── Input row ─────────────────────────────────────────────────────────────

    private JPanel buildInputRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(4, 0, 4, 0));

        inputField = new JTextField();
        inputField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.decode("#CCCCCC"), 1),
            new EmptyBorder(6, 10, 6, 10)));
        inputField.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendMessage(inputField.getText());
            }
        });

        sendBtn = new JButton("Send ↗");
        sendBtn.setFont(sendBtn.getFont().deriveFont(Font.BOLD, 13f));
        sendBtn.setBackground(Color.decode("#D03C1E"));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setOpaque(true);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.addActionListener(e -> sendMessage(inputField.getText()));

        JButton clearBtn = new JButton("Clear chat");
        clearBtn.setFont(clearBtn.getFont().deriveFont(12f));
        clearBtn.addActionListener(e -> clearChat());

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(Color.GRAY);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightBtns.setOpaque(false);
        rightBtns.add(clearBtn);
        rightBtns.add(sendBtn);

        row.add(inputField,  BorderLayout.CENTER);
        row.add(rightBtns,   BorderLayout.EAST);
        row.add(statusLabel, BorderLayout.SOUTH);
        return row;
    }

    // ── Styles ────────────────────────────────────────────────────────────────

    private void initStyles() {
        userStyle   = chatDoc.addStyle("user",   null);
        claudeStyle = chatDoc.addStyle("claude", null);
        timeStyle   = chatDoc.addStyle("time",   null);
        errorStyle  = chatDoc.addStyle("error",  null);
        labelStyle  = chatDoc.addStyle("label",  null);

        StyleConstants.setFontFamily(userStyle,   Font.SANS_SERIF);
        StyleConstants.setFontSize(userStyle, 13);
        StyleConstants.setBackground(userStyle, Color.decode("#EBF5FF"));

        StyleConstants.setFontFamily(claudeStyle, Font.SANS_SERIF);
        StyleConstants.setFontSize(claudeStyle, 13);
        StyleConstants.setBackground(claudeStyle, Color.decode("#F9F9F9"));

        StyleConstants.setFontFamily(timeStyle, Font.SANS_SERIF);
        StyleConstants.setFontSize(timeStyle, 10);
        StyleConstants.setForeground(timeStyle, Color.GRAY);

        StyleConstants.setFontFamily(errorStyle, Font.SANS_SERIF);
        StyleConstants.setFontSize(errorStyle, 12);
        StyleConstants.setForeground(errorStyle, Color.decode("#C0392B"));

        StyleConstants.setFontFamily(labelStyle, Font.SANS_SERIF);
        StyleConstants.setFontSize(labelStyle, 11);
        StyleConstants.setBold(labelStyle, true);
    }

    // ── Message rendering ─────────────────────────────────────────────────────

    private void appendWelcome() {
        try {
            Style welcome = chatDoc.addStyle("welcome", null);
            StyleConstants.setFontFamily(welcome, Font.SANS_SERIF);
            StyleConstants.setFontSize(welcome, 13);
            StyleConstants.setForeground(welcome, Color.decode("#555555"));
            chatDoc.insertString(chatDoc.getLength(),
                "👋  Hi! I'm your JMeter AI assistant powered by Claude.\n" +
                "Ask me anything about JMeter scripting, correlations, load test design, \n" +
                "assertions, parameterization, plugins, or performance testing best practices.\n\n",
                welcome);
        } catch (BadLocationException ignored) {}
    }

    private void appendUserMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                Style nameStyle = chatDoc.addStyle("uname", null);
                StyleConstants.setFontFamily(nameStyle, Font.SANS_SERIF);
                StyleConstants.setFontSize(nameStyle, 11);
                StyleConstants.setBold(nameStyle, true);
                StyleConstants.setForeground(nameStyle, Color.decode("#1A5276"));

                chatDoc.insertString(chatDoc.getLength(), "You  ", nameStyle);
                chatDoc.insertString(chatDoc.getLength(), LocalTime.now().format(TIME_FMT) + "\n", timeStyle);
                chatDoc.insertString(chatDoc.getLength(), text + "\n\n", userStyle);
                scrollToBottom();
            } catch (BadLocationException ignored) {}
        });
    }

    private void appendClaudeMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                Style nameStyle = chatDoc.addStyle("cname", null);
                StyleConstants.setFontFamily(nameStyle, Font.SANS_SERIF);
                StyleConstants.setFontSize(nameStyle, 11);
                StyleConstants.setBold(nameStyle, true);
                StyleConstants.setForeground(nameStyle, Color.decode("#922B21"));

                chatDoc.insertString(chatDoc.getLength(), "Claude  ", nameStyle);
                chatDoc.insertString(chatDoc.getLength(), LocalTime.now().format(TIME_FMT) + "\n", timeStyle);
                chatDoc.insertString(chatDoc.getLength(), text + "\n\n", claudeStyle);
                scrollToBottom();
            } catch (BadLocationException ignored) {}
        });
    }

    private void appendTypingIndicator() {
        SwingUtilities.invokeLater(() -> {
            try {
                Style typing = chatDoc.addStyle("typing", null);
                StyleConstants.setFontFamily(typing, Font.SANS_SERIF);
                StyleConstants.setFontSize(typing, 12);
                StyleConstants.setForeground(typing, Color.GRAY);
                StyleConstants.setItalic(typing, true);
                chatDoc.insertString(chatDoc.getLength(), "Claude is typing...\n", typing);
                scrollToBottom();
            } catch (BadLocationException ignored) {}
        });
    }

    private void removeLastLine() {
        SwingUtilities.invokeLater(() -> {
            try {
                String text = chatDoc.getText(0, chatDoc.getLength());
                int lastNewline = text.lastIndexOf('\n', text.length() - 2);
                if (lastNewline >= 0) {
                    chatDoc.remove(lastNewline + 1, chatDoc.getLength() - lastNewline - 1);
                }
            } catch (BadLocationException ignored) {}
        });
    }

    private void scrollToBottom() {
        chatPane.setCaretPosition(chatDoc.getLength());
    }

    // ── Send message ──────────────────────────────────────────────────────────

    private void sendMessage(String text) {
        text = text.trim();
        if (text.isEmpty()) return;

        final String message = text;
        inputField.setText("");
        sendBtn.setEnabled(false);
        statusLabel.setText("Claude is thinking...");
        statusLabel.setForeground(Color.decode("#E67E22"));

        appendUserMessage(message);
        conversationHistory.add(new String[]{"user", message});
        appendTypingIndicator();

        new Thread(() -> {
            try {
                // Build context from history (last 10 turns for token efficiency)
                StringBuilder context = new StringBuilder();
                int start = Math.max(0, conversationHistory.size() - 10);
                for (int i = start; i < conversationHistory.size() - 1; i++) {
                    String[] turn = conversationHistory.get(i);
                    context.append(turn[0].equals("user") ? "User: " : "Assistant: ")
                           .append(turn[1]).append("\n");
                }

                String response = claudeService.askJmeterQuestion(message, context.toString());
                conversationHistory.add(new String[]{"assistant", response});

                removeLastLine(); // remove "Claude is typing..."
                appendClaudeMessage(response);

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Ready");
                    statusLabel.setForeground(Color.GRAY);
                    sendBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                removeLastLine();
                SwingUtilities.invokeLater(() -> {
                    try {
                        chatDoc.insertString(chatDoc.getLength(),
                            "Error: " + ex.getMessage() + "\n\n", errorStyle);
                    } catch (BadLocationException ignored) {}
                    statusLabel.setText("Error: " + ex.getMessage());
                    statusLabel.setForeground(Color.RED);
                    sendBtn.setEnabled(true);
                });
            }
        }, "ChatWorker").start();
    }

    private void clearChat() {
        try {
            chatDoc.remove(0, chatDoc.getLength());
            conversationHistory.clear();
            appendWelcome();
            statusLabel.setText(" ");
        } catch (BadLocationException ignored) {}
    }
}
