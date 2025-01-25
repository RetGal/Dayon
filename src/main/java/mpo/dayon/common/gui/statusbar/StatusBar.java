package mpo.dayon.common.gui.statusbar;

import mpo.dayon.common.monitoring.BigBrother;
import mpo.dayon.common.monitoring.counter.Counter;
import mpo.dayon.common.utils.SystemUtilities;

import javax.swing.*;
import java.awt.*;
import java.util.TimerTask;

import static java.lang.String.format;
import static javax.swing.BoxLayout.LINE_AXIS;
import static javax.swing.SwingConstants.*;
import static mpo.dayon.common.babylon.Babylon.translate;

public class StatusBar extends JPanel {

    private static final int HEIGHT = 5;
    private static final Color DEFAULT_INDICATOR_COLOR = Color.darkGray;
    private static final String INITIAL_SESSION_DURATION = "00:00:00";
    private final JLabel portStateIndicator = stateIndicator();
    private final JLabel peerStateIndicator = stateIndicator();
    private final JLabel message = new JLabel();
    private final JLabel sessionDuration = new JLabel(INITIAL_SESSION_DURATION);
    private final JLabel keyboardLayout = new JLabel();
    private final JLabel capsLockIndicator = new JLabel();

    public StatusBar(int strutWidth) {
        setLayout(new BoxLayout(this, LINE_AXIS));
        add(Box.createHorizontalStrut(strutWidth));
        add(portStateIndicator);
        add(peerStateIndicator);
        add(Box.createHorizontalStrut(5));
        add(message);
        add(Box.createHorizontalGlue());
        addSeparator();
        addKeyboardLayout();
        addCapsLockIndicator();
    }

    private JLabel stateIndicator() {
        JLabel stateIndicator = new JLabel("\u25CF ");
        stateIndicator.setForeground(DEFAULT_INDICATOR_COLOR);
        return stateIndicator;
    }

    public void clearMessage() {
        message.setText(null);
    }

    public void setMessage(String message) {
        this.message.setText(message);
    }

    public void setPortStateIndicator(Color color) {
        portStateIndicator.setForeground(color);
    }

    public void resetPortStateIndicator() {
        portStateIndicator.setForeground(DEFAULT_INDICATOR_COLOR);
    }

    public void setPeerStateIndicator(Color color) {
        peerStateIndicator.setForeground(color);
    }

    public void resetPeerStateIndicator() {
        peerStateIndicator.setForeground(DEFAULT_INDICATOR_COLOR);
    }

    public void setSessionDuration(String sessionDuration) {
        this.sessionDuration.setText(sessionDuration);
    }

    public void resetSessionDuration() {
        this.sessionDuration.setText(INITIAL_SESSION_DURATION);
    }

    public void setKeyboardLayout(String keyboardLayout) {
        this.keyboardLayout.setText(keyboardLayout);
        this.keyboardLayout.setToolTipText(format("⌨ %s", keyboardLayout));
    }

    public String getKeyboardLayout() {
        return keyboardLayout.getText();
    }

    private void addKeyboardLayout() {
        add(keyboardLayout);
    }

    private void addCapsLockIndicator() {
        add(capsLockIndicator);
    }

    public boolean isCapsLockOn() {
        return !capsLockIndicator.getText().isBlank();
    }

    public void setCapsLockIndicator(boolean isCapsLockOn) {
        capsLockIndicator.setText(isCapsLockOn ? " ⛰ " : "");
        capsLockIndicator.setToolTipText("Caps Lock");
    }

    public <T> void addCounter(Counter<T> counter, int width) {
        JLabel label = createLabel(counter.getUid(), width);
        label.setToolTipText(counter.getShortDescription());
        counter.addListener((counter1, value) -> label.setText(counter1.formatInstantValue(value)));
        add(label);
    }

    public void addRamInfo() {
        JLabel label = createLabel("", 110);
        label.setHorizontalAlignment(RIGHT);
        BigBrother.get().registerRamInfo(new MemoryCounter(label));
        label.setToolTipText(translate("memory.info"));
        add(label);
    }

    public void addConnectionDuration() {
        sessionDuration.setHorizontalAlignment(RIGHT);
        sessionDuration.setPreferredSize(new Dimension(65, HEIGHT));
        sessionDuration.setToolTipText(translate("session.duration"));
        add(sessionDuration);
    }

    public void addSeparator() {
        add(new JToolBar.Separator(new Dimension(5, HEIGHT)));
    }

    private JLabel createLabel(String text, int width) {
        JLabel label = new JLabel(text);
        label.setHorizontalAlignment(CENTER);
        label.setPreferredSize(new Dimension(width, HEIGHT));
        return label;
    }

    private static class MemoryCounter extends TimerTask {
        private final JLabel lbl;

        private MemoryCounter(JLabel lbl) {
            this.lbl = lbl;
        }

        @Override
        public void run() {
            lbl.setText(SystemUtilities.getRamInfo());
        }
    }

}