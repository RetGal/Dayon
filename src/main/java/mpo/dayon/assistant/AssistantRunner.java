package mpo.dayon.assistant;

import mpo.dayon.assistant.gui.Assistant;
import mpo.dayon.assistant.gui.AssistantConfiguration;
import mpo.dayon.assistant.jetty.JettyLogger;
import mpo.dayon.common.babylon.Zion;
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
			System.setProperty("org.eclipse.jetty.util.log.announce", "false");
			System.setProperty("org.eclipse.jetty.LEVEL", "WARN");
			System.setProperty("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
			// ---------------------------------------------------------------------------------------------------------

			SystemUtilities.setApplicationName("dayon_assistant");
			System.setProperty("dayon.debug", "on");
			
			Zion.overrideLocale(args);

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