package mpo.dayon.common.gui.statusbar;

import mpo.dayon.common.monitoring.BigBrother;
import mpo.dayon.common.monitoring.counter.Counter;
import mpo.dayon.common.monitoring.counter.CounterListener;
import mpo.dayon.common.utils.SystemUtilities;

import javax.swing.*;
import java.awt.*;
import java.util.TimerTask;

import static mpo.dayon.common.babylon.Babylon.translate;

public class StatusBar extends JPanel {
    private final JLabel message = new JLabel();
    private final JLabel sessionDuration = new JLabel("00:00:00");

    public StatusBar() {
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        add(Box.createHorizontalStrut(5));
        add(message);
        add(Box.createHorizontalGlue());
    }

    public void clearMessage() {
        this.message.setText(null);
    }

    public void setMessage(String message) {
        this.message.setText(message);
    }

    public void setSessionDuration(String sessionDuration) {
        this.sessionDuration.setText(sessionDuration);
    }

    public void addCounter(Counter<?> counter, int width) {
        final JLabel lbl = new JLabel(counter.getUid());
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setSize(new Dimension(width, 5));
        lbl.setPreferredSize(new Dimension(width, 5));
        lbl.setToolTipText(counter.getShortDescription());
        counter.addListener((CounterListener) (counter1, value) -> lbl.setText(counter1.formatInstantValue(value)));
        add(lbl);
    }

    public void addRamInfo() {
        final JLabel lbl = new JLabel();
        lbl.setHorizontalAlignment(SwingConstants.CENTER);
        lbl.setSize(new Dimension(110, 5));
        lbl.setPreferredSize(new Dimension(110, 5));
        BigBrother.get().registerRamInfo(new MemoryCounter(lbl));
        lbl.setToolTipText(translate("memory.info"));
        add(lbl);
    }

    public void addConnectionDuration() {
        sessionDuration.setHorizontalAlignment(SwingConstants.CENTER);
        sessionDuration.setSize(new java.awt.Dimension(100, 5));
        sessionDuration.setPreferredSize(new java.awt.Dimension(100, 5));
        sessionDuration.setToolTipText(translate("session.duration"));
        add(sessionDuration);
    }

    public void addSeparator() {
        final JToolBar.Separator separator = new JToolBar.Separator();
        separator.setOrientation(SwingConstants.VERTICAL);
        add(separator);
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