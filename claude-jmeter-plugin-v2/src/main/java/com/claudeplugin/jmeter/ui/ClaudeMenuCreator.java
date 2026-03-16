package com.claudeplugin.jmeter.ui;

import org.apache.jmeter.gui.plugin.MenuCreator;
import org.apache.jmeter.util.JMeterUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * JMeter Menu Plugin entry point.
 *
 * This class implements MenuCreator which is how JMeter plugins add items
 * to the top menu bar. The plugin appears under:
 *   Tools > Claude AI Assistant
 *
 * JMeter discovers this class via the file:
 *   META-INF/services/org.apache.jmeter.gui.plugin.MenuCreator
 */
public class ClaudeMenuCreator implements MenuCreator {

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.TOOLS) {
            JMenuItem item = new JMenuItem("Claude AI Assistant");
            item.setToolTipText("Open Claude AI scripting assistant");
            item.addActionListener(e -> openClaudePanel());
            return new JMenuItem[]{ item };
        }
        return new JMenuItem[0];
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0]; // we use existing Tools menu
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    @Override
    public void localeChanged() {}

    // ── Open the Claude panel in a JFrame ────────────────────────────────────

    private void openClaudePanel() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Claude AI Assistant — JMeter");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(1000, 700);
            frame.setMinimumSize(new Dimension(800, 550));

            // Add the main panel
            ClaudeAssistantPanel panel = new ClaudeAssistantPanel();
            frame.setContentPane(panel);
            frame.setLocationRelativeTo(null); // centre on screen
            frame.setVisible(true);
        });
    }
}
