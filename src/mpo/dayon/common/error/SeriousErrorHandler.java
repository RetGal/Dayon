package mpo.dayon.common.error;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.common.babylon.Babylon;
import mpo.dayon.common.log.Log;

public abstract class SeriousErrorHandler {
	@Nullable
	private static JFrame frame;

	public static void warn(String message) {

		if (frame != null) {
			JOptionPane.showMessageDialog(frame, Babylon.translate("serious.error.msg1") + "\n" + Babylon.translate("serious.error.msg2", message) + "\n" + Babylon.translate("serious.error.msg3") ,
					Babylon.translate("serious.error"), JOptionPane.ERROR_MESSAGE);
		} else {
			Log.error("Unable to display error message "+message);
		}

	}

	public static void attachFrame(JFrame frame) {
		SeriousErrorHandler.frame = frame;
	}
}
