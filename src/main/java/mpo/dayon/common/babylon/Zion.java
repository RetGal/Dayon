package mpo.dayon.common.babylon;

import java.util.Arrays;
import java.util.Locale;

public class Zion {

	public static void overrideLocale(String[] args) {

		Arrays.stream(args).filter(arg -> arg.equalsIgnoreCase("de") || arg.equalsIgnoreCase("en") || arg.equalsIgnoreCase("fr")).findFirst()
				.ifPresent(arg -> Locale.setDefault(new Locale(arg)));

	}

}
