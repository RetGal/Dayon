package mpo.dayon.assisted;

import mpo.dayon.assisted.gui.Assisted;
import mpo.dayon.common.Runner;
import mpo.dayon.common.error.FatalErrorHandler;

import java.util.Map;

class AssistedRunner implements Runner {
	public static void main(String[] args) {
		try {
			Map<String, String> programArguments = Runner.extractProgramArgs(args);
			Runner.overrideLocale(programArguments.get("lang"));
			Runner.logAppInfo("dayon_assisted");

			final Assisted assisted = new Assisted();
			assisted.configure();
			assisted.start(programArguments.get("ah"), programArguments.get("ap"));

		} catch (Exception ex) {
			FatalErrorHandler.bye("The assisted is dead!", ex);
		}
	}
}
