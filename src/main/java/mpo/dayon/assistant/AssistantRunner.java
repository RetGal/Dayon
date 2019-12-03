package mpo.dayon.assistant;

import mpo.dayon.assistant.gui.Assistant;
import mpo.dayon.assistant.gui.AssistantConfiguration;
import mpo.dayon.common.Runner;
import mpo.dayon.common.error.FatalErrorHandler;

class AssistantRunner implements Runner {
	public static void main(String[] args) {
		try {
			Runner.overrideLocale(Runner.extractProgramArgs(args).get("lang"));
			Runner.logAppInfo("dayon_assistant");

			final Assistant assistant = new Assistant();
			assistant.configure(new AssistantConfiguration());
			assistant.start();

		} catch (Exception ex) {
			FatalErrorHandler.bye("The assistant is dead!", ex);
		}
	}
}