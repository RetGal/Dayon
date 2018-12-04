package mpo.dayon.assistant;

import mpo.dayon.assistant.gui.Assistant;
import mpo.dayon.assistant.gui.AssistantConfiguration;
import mpo.dayon.assistant.jetty.JettyLogger;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;

public class AssistantRunner {
	public static void main(String[] args) {
		try {
			// ---------------------------------------------------------------------------------------------------------
			// JETTY setup
			//
			System.setProperty("org.eclipse.jetty.util.log.class", JettyLogger.class.getName());
			// System.setProperty("org.eclipse.jetty.util.log.announce", "false");
			// System.setProperty("org.eclipse.jetty.LEVEL", "OFF");

			// System.setProperty("DEBUG", "on");
			// System.setProperty("VERBOSE", "on");
			System.setProperty("IGNORED", "on");
			// ---------------------------------------------------------------------------------------------------------

			SystemUtilities.setApplicationName("dayon_assistant");
			// System.setProperty("dayon.debug", "on");

			Log.info("============================================================================================");
			for (String line : SystemUtilities.getSystemProperties()) {
				Log.info(line);
			}
			Log.info("============================================================================================");

			final Assistant assistant = new Assistant();

			assistant.configure(new AssistantConfiguration());
			assistant.start();

		} catch (Exception ex) {
			FatalErrorHandler.bye("The assistant is dead!", ex);
		}
	}
}