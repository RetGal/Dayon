package mpo.dayon.assistant;

import mpo.dayon.assistant.gui.Assistant;
import mpo.dayon.common.Runner;

public class AssistantRunner {

	public static void main(String[] args) {
		Runner.main(args);
	}

	public static void launchAssistant() {
		final Assistant assistant = new Assistant();
		assistant.configure();
		assistant.start();
	}
}
