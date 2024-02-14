package mpo.dayon.common.gui.statusbar;

import mpo.dayon.common.monitoring.BigBrother;
import mpo.dayon.common.monitoring.counter.Counter;
import mpo.dayon.common.monitoring.counter.CounterListener;
import mpo.dayon.common.utils.SystemUtilities;

import javax.swing.*;
import java.awt.*;
import java.util.TimerTask;

import static javax.swing.BoxLayout.LINE_AXIS;
import static javax.swing.SwingConstants.*;
import static mpo.dayon.common.babylon.Babylon.translate;

public class StatusBar extends JPanel {
    
    private static final int HEIGHT = 5;
    private final JLabel message = new JLabel();
    private final JLabel sessionDuration = new JLabel("00:00:00");

    public StatusBar() {
        setLayout(new BoxLayout(this, LINE_AXIS));
        add(Box.createHorizontalStrut(10));
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
        final Dimension dimension = new Dimension(width, HEIGHT);
        lbl.setHorizontalAlignment(CENTER);
        lbl.setSize(dimension);
        lbl.setPreferredSize(dimension);
        lbl.setToolTipText(counter.getShortDescription());
        counter.addListener((CounterListener) (counter1, value) -> lbl.setText(counter1.formatInstantValue(value)));
        add(lbl);
    }

    public void addRamInfo() {
        final JLabel lbl = new JLabel();
        final Dimension dimension = new Dimension(110, HEIGHT);
        lbl.setHorizontalAlignment(CENTER);
        lbl.setSize(dimension);
        lbl.setPreferredSize(dimension);
        BigBrother.get().registerRamInfo(new MemoryCounter(lbl));
        lbl.setToolTipText(translate("memory.info"));
        add(lbl);
    }

    public void addConnectionDuration() {
        final Dimension dimension = new Dimension(65, HEIGHT);
        sessionDuration.setHorizontalAlignment(RIGHT);
        sessionDuration.setSize(dimension);
        sessionDuration.setPreferredSize(dimension);
        sessionDuration.setToolTipText(translate("session.duration"));
        add(sessionDuration);
    }

    public void addSeparator() {
        final JToolBar.Separator separator = new JToolBar.Separator();
        separator.setOrientation(VERTICAL);
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