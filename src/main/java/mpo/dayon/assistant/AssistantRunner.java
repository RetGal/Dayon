package mpo.dayon.assistant;

import mpo.dayon.assistant.gui.Assistant;
import mpo.dayon.common.Runner;

import java.util.Arrays;

public class AssistantRunner {

	public static void main(String[] args) {
		Runner.main(appendAssistant(args));
	}

	private static String[] appendAssistant(String[] args) {
		String[] combined = Arrays.copyOf(args, args.length + 1);
		String[] additional = new String[]{"assistant"};
		System.arraycopy(additional, 0, combined, args.length, 1);
		return combined;
	}

	public static void launchAssistant() {
		new Assistant();
	}
}
