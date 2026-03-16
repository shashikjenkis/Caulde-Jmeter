package com.claudeplugin.jmeter.ui;

import com.claudeplugin.jmeter.har.HarParser;
import com.claudeplugin.jmeter.service.ClaudeApiService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Main 6-tab Claude AI Assistant panel for JMeter.
 * Opens via: Tools → Claude AI Assistant
 */
public class ClaudeAssistantPanel extends JPanel {

    private static final Preferences PREFS = Preferences.userNodeForPackage(ClaudeAssistantPanel.class);
    private static final String PREF_API_KEY = "claude_api_key";

    ClaudeApiService claudeService;
    private JTextField apiKeyField;
    private JLabel statusLabel;
    private JTabbedPane tabbedPane;

    // Tab 1
    private JTextArea requirementInput, scriptOutput;
    private JComboBox<String> scriptTypeCombo, protocolCombo;

    // Tab 2
    private JLabel harFileLabel;
    private File selectedHarFile;
    private JTextArea harOutput;

    // Tab 3
    private JTextArea correlationInput, correlationOutput;

    // Tab 4
    private JTextArea paramInput, paramOutput;

    public ClaudeAssistantPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);
        String savedKey = PREFS.get(PREF_API_KEY, "");
        if (!savedKey.isEmpty()) claudeService = new ClaudeApiService(savedKey);
        add(buildTopBar(savedKey), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);
    }

    private JPanel buildTopBar(String savedKey) {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 0, 10, 0));
        apiKeyField = new JTextField(savedKey.isEmpty() ? "sk-ant-..." : savedKey, 32);
        apiKeyField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        apiKeyField.setEchoChar('\u2022');
        JButton saveBtn = new JButton("Save Key");
        saveBtn.addActionListener(e -> {
            String key = apiKeyField.getText().trim();
            if (key.startsWith("sk-ant-")) {
                claudeService = new ClaudeApiService(key);
                PREFS.put(PREF_API_KEY, key);
                setStatus("API key saved \u2713", Color.decode("#1D9E75"));
            } else setStatus("Invalid key — must start with sk-ant-", Color.RED);
        });
        statusLabel = new JLabel(savedKey.isEmpty() ? "Enter your Anthropic API key" : "API key loaded \u2713");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(savedKey.isEmpty() ? Color.GRAY : Color.decode("#1D9E75"));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(new JLabel("API Key:")); left.add(apiKeyField); left.add(saveBtn); left.add(statusLabel);
        bar.add(left, BorderLayout.WEST);
        return bar;
    }

    private JTabbedPane buildTabs() {
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(13f));
        tabbedPane.addTab("Script Generator",    buildScriptGeneratorTab());
        tabbedPane.addTab("HAR Importer",         buildHarImporterTab());
        tabbedPane.addTab("Correlations",         buildCorrelationTab());
        tabbedPane.addTab("Parameterization",     buildParamTab());
        tabbedPane.addTab("JMeter Chat",          buildChatTab());
        tabbedPane.addTab("Test Plan Optimizer",  buildOptimizerTab());
        return tabbedPane;
    }

    private JPanel buildScriptGeneratorTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 0, 0, 0)); p.setOpaque(false);
        JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); opts.setOpaque(false);
        scriptTypeCombo = new JComboBox<>(new String[]{"JMX Test Plan", "Groovy (JSR223)", "BeanShell"});
        protocolCombo   = new JComboBox<>(new String[]{"HTTP/HTTPS", "WebSocket", "JDBC", "MQTT", "gRPC"});
        opts.add(new JLabel("Type:")); opts.add(scriptTypeCombo);
        opts.add(new JLabel("Protocol:")); opts.add(protocolCombo);
        requirementInput = buildMonoTextArea(7);
        requirementInput.setText("Example: 100 users ramp up 30s, POST /api/login JSON, extract JWT, GET /api/dashboard Bearer auth, assert < 2s, run 5 min");
        scriptOutput = buildMonoTextArea(20);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            wrapLabeled("Describe your test:", addSP(requirementInput)),
            wrapLabeled("Generated JMX:", addSP(scriptOutput)));
        split.setDividerLocation(180); split.setResizeWeight(0.25);
        p.add(opts, BorderLayout.NORTH); p.add(split, BorderLayout.CENTER);
        p.add(buildBtnRow(
            "Generate JMX", e -> runBg(() -> {
                if (!chk()) return;
                setStatus("Generating...", Color.decode("#EF9F27"));
                try { setTA(scriptOutput, claudeService.generateJmxScript(requirementInput.getText()));
                    setStatus("Done \u2713", Color.decode("#1D9E75")); }
                catch (Exception ex) { setStatus("Error: "+ex.getMessage(), Color.RED); }
            }),
            "Copy", e -> clip(scriptOutput.getText())
        ), BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildHarImporterTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 0, 0, 0)); p.setOpaque(false);
        harFileLabel = new JLabel("No file selected");
        harFileLabel.setForeground(Color.GRAY);
        JButton browse = new JButton("Browse HAR...");
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("HAR files", "har"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedHarFile = fc.getSelectedFile();
                harFileLabel.setText(selectedHarFile.getName() + " ("+(selectedHarFile.length()/1024)+" KB)");
                harFileLabel.setForeground(Color.decode("#0F6E56"));
            }
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); top.setOpaque(false);
        top.add(browse); top.add(harFileLabel);
        top.add(new JLabel("  Export from Chrome DevTools: Network tab \u2192 Right-click \u2192 Save all as HAR"));
        harOutput = buildMonoTextArea(28);
        p.add(top, BorderLayout.NORTH);
        p.add(addSP(harOutput), BorderLayout.CENTER);
        p.add(buildBtnRow(
            "Analyze HAR + Generate JMX", e -> runBg(() -> {
                if (!chk()) return;
                if (selectedHarFile == null) { setStatus("Select a HAR file first", Color.RED); return; }
                setStatus("Parsing HAR...", Color.decode("#EF9F27"));
                try {
                    List<HarParser.HarRequest> reqs = HarParser.parse(selectedHarFile);
                    setStatus("Sending "+reqs.size()+" requests to Claude...", Color.decode("#EF9F27"));
                    setTA(harOutput, claudeService.analyzeHar(HarParser.toClaudeSummary(reqs)));
                    setStatus(reqs.size()+" requests processed \u2713", Color.decode("#1D9E75"));
                } catch (Exception ex) { setStatus("Error: "+ex.getMessage(), Color.RED); }
            }),
            "Copy output", e -> clip(harOutput.getText())
        ), BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildCorrelationTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 0, 0, 0)); p.setOpaque(false);
        correlationInput = buildMonoTextArea(10); correlationOutput = buildMonoTextArea(16);
        correlationInput.setText("<!-- Paste your JMX here -->");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            wrapLabeled("JMX Input:", addSP(correlationInput)),
            wrapLabeled("Correlation suggestions:", addSP(correlationOutput)));
        split.setDividerLocation(220);
        p.add(split, BorderLayout.CENTER);
        p.add(buildBtnRow(
            "Find Correlations", e -> runBg(() -> {
                if (!chk()) return;
                setStatus("Analyzing...", Color.decode("#EF9F27"));
                try { setTA(correlationOutput, claudeService.suggestCorrelations(correlationInput.getText()));
                    setStatus("Done \u2713", Color.decode("#1D9E75")); }
                catch (Exception ex) { setStatus("Error: "+ex.getMessage(), Color.RED); }
            }),
            "Load JMX file", e -> loadFile(correlationInput),
            "Copy", e -> clip(correlationOutput.getText())
        ), BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildParamTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(new EmptyBorder(10, 0, 0, 0)); p.setOpaque(false);
        paramInput = buildMonoTextArea(10); paramOutput = buildMonoTextArea(16);
        paramInput.setText("<!-- Paste your JMX here -->");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            wrapLabeled("JMX Input:", addSP(paramInput)),
            wrapLabeled("Suggestions:", addSP(paramOutput)));
        split.setDividerLocation(220);
        p.add(split, BorderLayout.CENTER);
        p.add(buildBtnRow(
            "Suggest Parameterization", e -> runBg(() -> {
                if (!chk()) return;
                setStatus("Analyzing...", Color.decode("#EF9F27"));
                try { setTA(paramOutput, claudeService.suggestParameterization(paramInput.getText()));
                    setStatus("Done \u2713", Color.decode("#1D9E75")); }
                catch (Exception ex) { setStatus("Error: "+ex.getMessage(), Color.RED); }
            }),
            "Load JMX file", e -> loadFile(paramInput),
            "Copy", e -> clip(paramOutput.getText())
        ), BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildChatTab() {
        JPanel w = new JPanel(new BorderLayout()); w.setOpaque(false);
        w.setBorder(new EmptyBorder(6, 0, 0, 0));
        w.add(new ChatTab(claudeService, this::chkVoid), BorderLayout.CENTER);
        return w;
    }

    private JPanel buildOptimizerTab() {
        JPanel w = new JPanel(new BorderLayout()); w.setOpaque(false);
        w.setBorder(new EmptyBorder(6, 0, 0, 0));
        w.add(new OptimizerTab(claudeService), BorderLayout.CENTER);
        return w;
    }

    // helpers
    boolean chk() {
        if (claudeService == null) { SwingUtilities.invokeLater(() -> setStatus("Enter and save API key first", Color.RED)); return false; }
        return true;
    }
    void chkVoid() { chk(); }
    void setStatus(String m, Color c) { SwingUtilities.invokeLater(() -> { statusLabel.setText(m); statusLabel.setForeground(c); }); }
    void setTA(JTextArea a, String t) { SwingUtilities.invokeLater(() -> { a.setText(t); a.setCaretPosition(0); }); }
    void runBg(Runnable r) { new Thread(r, "ClaudeWorker").start(); }
    void clip(String t) { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(t), null); setStatus("Copied \u2713", Color.decode("#1D9E75")); }
    void loadFile(JTextArea target) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("JMX files", "jmx"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try { String c = new String(java.nio.file.Files.readAllBytes(fc.getSelectedFile().toPath())); target.setText(c); target.setCaretPosition(0); }
            catch (Exception ex) { setStatus("Load error: "+ex.getMessage(), Color.RED); }
        }
    }
    private JTextArea buildMonoTextArea(int rows) { JTextArea t = new JTextArea(rows, 60); t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); t.setTabSize(2); return t; }
    private JScrollPane addSP(JTextArea a) { return new JScrollPane(a); }
    private JPanel wrapLabeled(String lbl, JComponent c) {
        JPanel p = new JPanel(new BorderLayout(0, 4)); p.setOpaque(false);
        JLabel l = new JLabel(lbl); l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        p.add(l, BorderLayout.NORTH); p.add(c, BorderLayout.CENTER); return p;
    }
    private JPanel buildBtnRow(Object... args) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6)); row.setOpaque(false);
        for (int i = 0; i < args.length; i += 2) {
            JButton b = new JButton((String) args[i]);
            b.setFont(b.getFont().deriveFont(12f));
            b.addActionListener((java.awt.event.ActionListener) args[i+1]);
            if (i == 0) { b.setBackground(Color.decode("#D03C1E")); b.setForeground(Color.WHITE); b.setOpaque(true); b.setBorderPainted(false); }
            row.add(b);
        }
        return row;
    }
}
