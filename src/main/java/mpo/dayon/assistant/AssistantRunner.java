package mpo.dayon.assistant;

import mpo.dayon.assistant.gui.Assistant;
import mpo.dayon.common.Runner;

import java.util.Arrays;
import java.util.Map;

import static mpo.dayon.common.Runner.getCustomTokenServerUrls;
import static mpo.dayon.common.Runner.readPresetFile;

public class AssistantRunner {

	public static void main(String[] args) {
		Runner.main(appendAssistant(args));
	}

	private static String[] appendAssistant(String[] args) {
		String[] combined = Arrays.copyOf(args, args.length + 1);
		String[] additional = {"assistant"};
		System.arraycopy(additional, 0, combined, args.length, 1);
		return combined;
	}

	public static void launchAssistant(String language) {
		final Map<String, String> config = readPresetFile("assistant.yaml");
		new Assistant(getCustomTokenServerUrls(config), language);
	}
}
