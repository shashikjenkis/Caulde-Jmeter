package com.claudeplugin.jmeter.ui;

import com.claudeplugin.jmeter.service.ClaudeApiService;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * TAB 6: Test Plan Optimizer
 *
 * Pastes or loads a JMX file, then Claude performs a deep review covering:
 *  - Thread group configuration (ramp-up, think time, teardown)
 *  - Missing assertions, timers, listeners
 *  - Correlation gaps
 *  - Parameterization opportunities
 *  - Resource leaks, hard-coded hosts/ports
 *  - Protocol best practices (HTTP/2, keepalive, connection pooling)
 *  - Performance anti-patterns
 *  - Scoring: gives a 0-100 "test plan health score"
 *
 * It produces two outputs:
 *  1. A scored review with issues categorized as Critical / Warning / Suggestion
 *  2. An optimized JMX with all fixes applied
 */
public class OptimizerTab extends JPanel {

    private final ClaudeApiService claudeService;

    private JTextArea jmxInput;
    private JTextPane reviewOutput;      // styled HTML-like review
    private JTextArea optimizedJmxOutput;
    private JLabel scoreLabel;
    private JLabel statusLabel;
    private JTabbedPane outputTabs;

    public OptimizerTab(ClaudeApiService claudeService) {
        this.claudeService = claudeService;
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 0, 0, 0));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildMain(),   BorderLayout.CENTER);
        add(buildBtns(),   BorderLayout.SOUTH);
    }

    // ── Score badge + status ──────────────────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 0, 6, 0));

        // Score badge
        JPanel scoreBadge = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scoreBadge.setOpaque(false);
        JLabel scoreLbl = new JLabel("Health Score:");
        scoreLbl.setFont(scoreLbl.getFont().deriveFont(12f));

        scoreLabel = new JLabel("—");
        scoreLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        scoreLabel.setForeground(Color.decode("#888888"));

        JLabel scoreNote = new JLabel(" / 100");
        scoreNote.setFont(scoreNote.getFont().deriveFont(12f));
        scoreNote.setForeground(Color.GRAY);

        scoreBadge.add(scoreLbl);
        scoreBadge.add(scoreLabel);
        scoreBadge.add(scoreNote);

        statusLabel = new JLabel("Load or paste your JMX test plan, then click Analyze");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(Color.GRAY);

        bar.add(scoreBadge, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    // ── Split: input left, output right ──────────────────────────────────────

    private JSplitPane buildMain() {
        // Input
        jmxInput = new JTextArea();
        jmxInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jmxInput.setTabSize(2);
        jmxInput.setText("<!-- Paste your JMX test plan here, or use the Load button below -->");

        JPanel inputPanel = new JPanel(new BorderLayout(0, 4));
        inputPanel.setOpaque(false);
        JLabel inLbl = new JLabel("JMX Input");
        inLbl.setFont(inLbl.getFont().deriveFont(Font.BOLD, 12f));
        inputPanel.add(inLbl, BorderLayout.NORTH);
        inputPanel.add(new JScrollPane(jmxInput), BorderLayout.CENTER);

        // Output (tabbed: Review | Optimized JMX)
        outputTabs = new JTabbedPane();
        outputTabs.setFont(outputTabs.getFont().deriveFont(12f));

        // Review pane
        reviewOutput = new JTextPane();
        reviewOutput.setEditable(false);
        reviewOutput.setContentType("text/html");
        reviewOutput.setBorder(new EmptyBorder(8, 8, 8, 8));
        reviewOutput.setText(buildPlaceholderHtml());
        outputTabs.addTab("📋  Review", new JScrollPane(reviewOutput));

        // Optimized JMX pane
        optimizedJmxOutput = new JTextArea();
        optimizedJmxOutput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        optimizedJmxOutput.setEditable(false);
        outputTabs.addTab("✅  Optimized JMX", new JScrollPane(optimizedJmxOutput));

        JPanel outputPanel = new JPanel(new BorderLayout(0, 4));
        outputPanel.setOpaque(false);
        JLabel outLbl = new JLabel("Analysis Output");
        outLbl.setFont(outLbl.getFont().deriveFont(Font.BOLD, 12f));
        outputPanel.add(outLbl, BorderLayout.NORTH);
        outputPanel.add(outputTabs, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, outputPanel);
        split.setDividerLocation(340);
        split.setResizeWeight(0.38);
        return split;
    }

    private JPanel buildBtns() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);

        JButton analyzeBtn = new JButton("Analyze & Optimize ↗");
        styleBtn(analyzeBtn, Color.decode("#D03C1E"), Color.WHITE);
        analyzeBtn.addActionListener(e -> runAnalysis());

        JButton loadBtn = new JButton("Load .jmx file");
        loadBtn.addActionListener(e -> loadJmxFile());

        JButton copyOptBtn = new JButton("Copy optimized JMX");
        copyOptBtn.addActionListener(e -> {
            String txt = optimizedJmxOutput.getText();
            if (!txt.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(txt), null);
                setStatus("Optimized JMX copied ✓", Color.decode("#1D9E75"));
            }
        });

        JButton saveOptBtn = new JButton("Save optimized JMX");
        saveOptBtn.addActionListener(e -> saveJmxFile());

        row.add(analyzeBtn);
        row.add(loadBtn);
        row.add(copyOptBtn);
        row.add(saveOptBtn);
        return row;
    }

    // ── Core analysis ─────────────────────────────────────────────────────────

    private void runAnalysis() {
        String jmx = jmxInput.getText().trim();
        if (jmx.isEmpty() || jmx.startsWith("<!--")) {
            setStatus("Please load or paste a JMX test plan first", Color.RED);
            return;
        }

        setStatus("Claude is analyzing your test plan...", Color.decode("#E67E22"));
        scoreLabel.setText("…");
        scoreLabel.setForeground(Color.decode("#E67E22"));
        reviewOutput.setText("<html><body style='font-family:sans-serif;font-size:12px;color:#666;padding:10px'>Analyzing...</body></html>");

        new Thread(() -> {
            try {
                String systemPrompt = """
                    You are a world-class JMeter performance testing expert performing a deep audit.
                    
                    Analyze the JMX test plan and produce a structured report in this EXACT format:
                    
                    ---SCORE---
                    [integer 0-100 reflecting overall test plan health]
                    
                    ---CRITICAL---
                    [List issues that WILL cause incorrect results or test failure. Format each as:
                    ❌ ISSUE: <title>
                    WHY: <why this is a problem>
                    FIX: <exact fix with JMX snippet if relevant>]
                    
                    ---WARNINGS---
                    [List issues that MAY cause problems. Format each as:
                    ⚠️ WARNING: <title>
                    WHY: <explanation>
                    FIX: <recommendation>]
                    
                    ---SUGGESTIONS---
                    [List best-practice improvements. Format each as:
                    💡 SUGGESTION: <title>
                    BENEFIT: <what improves>
                    HOW: <implementation detail>]
                    
                    ---SUMMARY---
                    [2-3 sentence overall assessment]
                    
                    ---OPTIMIZED_JMX---
                    [Complete, corrected JMX with ALL critical issues and warnings fixed.
                    Add comments showing what was changed. Output valid XML starting with <?xml]
                    
                    Check specifically for:
                    - Thread group: ramp-up time, loop count, teardown thread group present?
                    - Missing ConstantTimer or Gaussian Timer (think time)
                    - Missing Response Assertions (status codes, content)
                    - Missing Duration Assertion (prevent infinite responses)
                    - Hardcoded host/port vs ${__P(host)} properties
                    - Dynamic values not correlated (session tokens, CSRF, ViewState)
                    - Hardcoded test data vs CSV Data Set Config
                    - Missing HTTP Cookie Manager, HTTP Cache Manager
                    - Connection keepalive settings
                    - Missing teardown thread group for cleanup
                    - View Results Tree in final test (must be disabled for load tests)
                    - Listeners that hurt performance (View Results in Table, etc.)
                    - Missing Summary Report or Aggregate Report
                    - Content-Type headers for POST requests
                    - Missing user.properties for environment config
                    """;

                String result = claudeService.callRaw(systemPrompt,
                    "Analyze and optimize this JMeter test plan:\n\n" + jmx);

                // Parse score
                int score = 50;
                if (result.contains("---SCORE---")) {
                    try {
                        String scoreSection = result.split("---SCORE---")[1].split("---")[0].trim();
                        score = Integer.parseInt(scoreSection.replaceAll("[^0-9]", "").substring(0, Math.min(3, scoreSection.replaceAll("[^0-9]", "").length())));
                        score = Math.max(0, Math.min(100, score));
                    } catch (Exception ignored) {}
                }

                // Extract optimized JMX
                String optimizedJmx = "";
                if (result.contains("---OPTIMIZED_JMX---")) {
                    optimizedJmx = result.split("---OPTIMIZED_JMX---")[1].trim();
                }

                // Build HTML for review pane
                String reviewHtml = buildReviewHtml(result, score);

                final int finalScore = score;
                final String finalOptimized = optimizedJmx;

                SwingUtilities.invokeLater(() -> {
                    reviewOutput.setContentType("text/html");
                    reviewOutput.setText(reviewHtml);
                    reviewOutput.setCaretPosition(0);

                    if (!finalOptimized.isEmpty()) {
                        optimizedJmxOutput.setText(finalOptimized);
                        optimizedJmxOutput.setCaretPosition(0);
                    }

                    scoreLabel.setText(String.valueOf(finalScore));
                    scoreLabel.setForeground(
                        finalScore >= 75 ? Color.decode("#1D9E75") :
                        finalScore >= 50 ? Color.decode("#E67E22") :
                        Color.decode("#C0392B"));

                    setStatus("Analysis complete — " + finalScore + "/100 health score", Color.decode("#1D9E75"));
                    outputTabs.setSelectedIndex(0);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Error: " + ex.getMessage(), Color.RED);
                    reviewOutput.setText("<html><body style='color:red;padding:10px'>Error: " + ex.getMessage() + "</body></html>");
                });
            }
        }, "OptimizerWorker").start();
    }

    // ── HTML renderer for review output ──────────────────────────────────────

    private String buildReviewHtml(String rawResult, int score) {
        String color = score >= 75 ? "#1D9E75" : score >= 50 ? "#E67E22" : "#C0392B";

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Arial,sans-serif;font-size:12px;padding:10px;line-height:1.6'>");

        // Score header
        html.append("<div style='background:").append(color)
            .append(";color:white;padding:10px 14px;border-radius:6px;margin-bottom:12px;font-size:14px;font-weight:bold'>")
            .append("Health Score: ").append(score).append(" / 100 — ")
            .append(score >= 75 ? "Good test plan" : score >= 50 ? "Needs improvements" : "Significant issues found")
            .append("</div>");

        // Parse and render sections
        String[] sections = {"CRITICAL", "WARNINGS", "SUGGESTIONS", "SUMMARY"};
        String[] sectionColors = {"#FDECEA", "#FFF8E1", "#E8F5E9", "#E3F2FD"};
        String[] borderColors  = {"#E53935", "#F9A825", "#43A047", "#1E88E5"};
        String[] sectionTitles = {"❌ Critical Issues", "⚠️ Warnings", "💡 Suggestions", "📋 Summary"};

        for (int i = 0; i < sections.length; i++) {
            String section = sections[i];
            if (rawResult.contains("---" + section + "---")) {
                String[] parts = rawResult.split("---" + section + "---");
                if (parts.length > 1) {
                    String content = parts[1].split("---")[0].trim();
                    if (!content.isEmpty()) {
                        html.append("<div style='background:").append(sectionColors[i])
                            .append(";border-left:4px solid ").append(borderColors[i])
                            .append(";padding:10px 14px;border-radius:4px;margin-bottom:10px'>")
                            .append("<strong>").append(sectionTitles[i]).append("</strong><br/><br/>")
                            .append(content.replace("\n", "<br/>").replace("❌", "❌").replace("⚠️", "⚠️").replace("💡", "💡"))
                            .append("</div>");
                    }
                }
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    private String buildPlaceholderHtml() {
        return "<html><body style='font-family:Arial,sans-serif;font-size:12px;color:#888;padding:16px;line-height:2'>" +
               "<b>Test Plan Optimizer</b><br/>" +
               "Claude will review your JMX for:<br/>" +
               "❌ Critical issues that break tests<br/>" +
               "⚠️ Warnings that affect accuracy<br/>" +
               "💡 Best practice suggestions<br/>" +
               "📊 Overall health score (0–100)<br/>" +
               "✅ A fully corrected JMX file" +
               "</body></html>";
    }

    // ── File helpers ──────────────────────────────────────────────────────────

    private void loadJmxFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("JMeter test plans (*.jmx)", "jmx"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String content = new String(Files.readAllBytes(fc.getSelectedFile().toPath()));
                jmxInput.setText(content);
                jmxInput.setCaretPosition(0);
                setStatus("Loaded: " + fc.getSelectedFile().getName(), Color.decode("#1D9E75"));
            } catch (IOException ex) {
                setStatus("Error loading file: " + ex.getMessage(), Color.RED);
            }
        }
    }

    private void saveJmxFile() {
        String content = optimizedJmxOutput.getText();
        if (content.isEmpty()) { setStatus("No optimized JMX to save yet", Color.RED); return; }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("optimized_test_plan.jmx"));
        fc.setFileFilter(new FileNameExtensionFilter("JMX files", "jmx"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(fc.getSelectedFile().toPath(), content.getBytes());
                setStatus("Saved: " + fc.getSelectedFile().getName(), Color.decode("#1D9E75"));
            } catch (IOException ex) {
                setStatus("Error saving: " + ex.getMessage(), Color.RED);
            }
        }
    }

    private void setStatus(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setForeground(color);
        });
    }

    private void styleBtn(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
    }
}
