package mpo.dayon.common.gui.statusbar;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.TimerTask;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.assistant.monitoring.BigBrother;
import mpo.dayon.assistant.monitoring.counter.Counter;
import mpo.dayon.assistant.monitoring.counter.CounterListener;
import mpo.dayon.common.utils.SystemUtilities;

public class StatusBar extends JPanel {
	private static final long serialVersionUID = -1042881354931006761L;
	private final JLabel message = new JLabel();

	public StatusBar() {
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
		setBorder(new EtchedBorder() {
			private static final long serialVersionUID = -3642319160243656549L;

			@Override
			public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
				g.translate(x, y);

				g.setColor(etchType == LOWERED ? getShadowColor(c) : getHighlightColor(c));
				g.drawLine(0, 0, width, 0);

				g.setColor(etchType == LOWERED ? getHighlightColor(c) : getShadowColor(c));
				g.drawLine(1, 1, width, 1);

				g.translate(-x, -y);
			}
		});

		add(Box.createHorizontalStrut(5));
		add(message);
		add(Box.createHorizontalGlue());
	}

	public void clearMessage() {
		this.message.setText(null);
	}

	public void setMessage(@Nullable String message) {
		this.message.setText(message);
	}

	public void addCounter(Counter<?> counter, int width) {
		final JLabel lbl = new JLabel(counter.getUid());

		lbl.setHorizontalAlignment(SwingConstants.CENTER);

		lbl.setSize(new Dimension(width, 5));
		lbl.setPreferredSize(new Dimension(width, 5));

		lbl.setToolTipText(counter.getShortDescription());

		counter.addListener(new CounterListener() {
			public void onInstantValueUpdated(Counter counter, Object value) {
				lbl.setText(counter.formatInstantValue(value));
			}
		});

		add(lbl);
	}

	public void addRamInfo() {
		final JLabel lbl = new JLabel();

		lbl.setHorizontalAlignment(SwingConstants.CENTER);

		lbl.setSize(new Dimension(100, 5));
		lbl.setPreferredSize(new Dimension(100, 5));

		BigBrother.get().registerRamInfo(new TimerTask() {
			@Override
			public void run() {
				lbl.setText(SystemUtilities.getRamInfo());
			}
		});

		add(lbl);
	}

	public void addSeparator() {
		final JToolBar.Separator separator = new JToolBar.Separator();
		separator.setOrientation(SwingConstants.VERTICAL);

		add(separator);
	}
}