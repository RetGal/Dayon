package mpo.dayon.assistant;

import mpo.dayon.assistant.gui.Assistant;
import mpo.dayon.common.Runner;
import mpo.dayon.common.error.FatalErrorHandler;

import javax.swing.SwingUtilities;

class AssistantRunner implements Runner {
	public static void main(String[] args) {
		try {
			Runner.setDebug(args);
			Runner.overrideLocale(Runner.extractProgramArgs(args).get("lang"));
			Runner.disableDynamicScale();
			Runner.logAppInfo("dayon_assistant");
			SwingUtilities.invokeLater(AssistantRunner::launchAssistant);
		} catch (Exception ex) {
			FatalErrorHandler.bye("The assistant is dead!", ex);
		}
	}

	private static void launchAssistant() {
		final Assistant assistant = new Assistant();
		assistant.configure();
		assistant.start();
	}
}
