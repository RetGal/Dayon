package mpo.dayon.common.babylon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.junit.jupiter.api.Test;

public class ZionTest {
	
	@Test
	public void shouldSetExpectedLocale() {
		// given
		String lang = "fr";
		String before = Locale.getDefault().toLanguageTag();
		if (before.equals(lang)) {
			lang = "de";
		}
		String[] args = {lang};
		// when
		Zion.overrideLocale(args);
		// then
		assertEquals(lang, Locale.getDefault().toLanguageTag());
	}
	
	@Test
	public void shouldNotSetUnsuportedLanguage() {
		// given
		String lang = "tr";
		String before = Locale.getDefault().toLanguageTag();
		if (before.equals(lang)) {
			lang = "ru";
		}
		String[] args = {lang};
		// when
		Zion.overrideLocale(args);
		// then
		assertEquals(before, Locale.getDefault().toLanguageTag());
	}	
	
}
