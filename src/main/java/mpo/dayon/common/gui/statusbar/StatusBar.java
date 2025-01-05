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
    private final JLabel message = new JLabel();
    private final JLabel sessionDuration = new JLabel("00:00:00");
    private final JLabel keyboardLayout = new JLabel();
    private final JLabel capsLockIndicator = new JLabel();

    public StatusBar() {
        setLayout(new BoxLayout(this, LINE_AXIS));
        add(Box.createHorizontalStrut(10));
        add(message);
        add(Box.createHorizontalGlue());
        addSeparator();
        addKeyboardLayout();
        addCapsLockIndicator();
    }

    public void clearMessage() {
        message.setText(null);
    }

    public void setMessage(String message) {
        this.message.setText(message);
    }

    public void setSessionDuration(String sessionDuration) {
        this.sessionDuration.setText(sessionDuration);
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