package mpo.dayon.common.preference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreferencesTest {

    private static final String PREFIX = "test.preferences.";

    private static String key(String name) {
        return PREFIX + name;
    }

    @Test
    void returnsAlwaysTheSameInstance() {
        // when, then
        assertSame(Preferences.getPreferences(), Preferences.getPreferences());
    }

    @Test
    void returnsTheDefaultValueForAnUnknownPreference() {
        // given
        final Preferences preferences = Preferences.getPreferences();

        // when, then
        assertEquals("fallback", preferences.getStringPreference(key("unknown"), "fallback"));
        assertEquals(42, preferences.getIntPreference(key("unknown"), 42));
        assertEquals(4.2d, preferences.getDoublePreference(key("unknown"), 4.2d));
        assertTrue(preferences.getBooleanPreference(key("unknown"), true));
    }

    @Test
    void returnsTheValuesOfAnUpdatedPreference() {
        // given
        final Preferences preferences = Preferences.getPreferences();
        final Preferences.Props props = new Preferences.Props();
        props.set(key("string"), "value");
        props.set(key("int"), "-17");
        props.set(key("double"), "-4.2");
        props.set(key("boolean"), "true");
        preferences.update(props);

        // when, then
        assertEquals("value", preferences.getStringPreference(key("string"), "fallback"));
        assertEquals(17, preferences.getIntPreference(key("int"), 42));
        assertEquals(4.2d, preferences.getDoublePreference(key("double"), 0));
        assertTrue(preferences.getBooleanPreference(key("boolean"), false));
    }

    @Test
    void returnsTheRequestedEnumPreference() {
        // given
        final Preferences preferences = Preferences.getPreferences();
        final Preferences.Props props = new Preferences.Props();
        props.set(key("enum"), String.valueOf(TestEnum.SECOND.ordinal()));
        preferences.update(props);

        // when, then
        assertEquals(TestEnum.SECOND, preferences.getEnumPreference(key("enum"), TestEnum.FIRST, TestEnum.values()));
        assertEquals(TestEnum.FIRST, preferences.getEnumPreference(key("unknown"), TestEnum.FIRST, TestEnum.values()));
    }

    @Test
    void returnsTheDefaultValueForAnUnknownEnumOrdinal() {
        // given
        final Preferences preferences = Preferences.getPreferences();
        final Preferences.Props props = new Preferences.Props();
        props.set(key("enum"), String.valueOf(TestEnum.values().length));
        preferences.update(props);

        // when, then
        assertEquals(TestEnum.FIRST, preferences.getEnumPreference(key("enum"), TestEnum.FIRST, TestEnum.values()));
    }

    @Test
    void throwsForMalformedNumbers() {
        // given
        final Preferences preferences = Preferences.getPreferences();
        final Preferences.Props props = new Preferences.Props();
        props.set(key("malformed"), "snafu");
        preferences.update(props);

        final String malformedKey = key("malformed");

        // when, then
        assertThrows(NumberFormatException.class, () -> preferences.getIntPreference(malformedKey, 42));
        assertThrows(NumberFormatException.class, () -> preferences.getDoublePreference(malformedKey, 4.2d));
    }

    @Test
    void removesClearedPreferences() {
        // given
        final Preferences preferences = Preferences.getPreferences();
        final Preferences.Props props = new Preferences.Props();
        props.set(key("cleared"), "value");
        preferences.update(props);
        assertEquals("value", preferences.getStringPreference(key("cleared"), null));

        // when
        final Preferences.Props clearProps = new Preferences.Props();
        clearProps.clear(key("cleared"));
        preferences.update(clearProps);

        // then
        assertEquals("fallback", preferences.getStringPreference(key("cleared"), "fallback"));
    }

    @Test
    void propsKeepsItsEntries() {
        // given
        final Preferences.Props props = new Preferences.Props();

        // when
        props.set("name", "value");
        props.clear("removed");

        // then
        assertEquals("value", props.getEntries().get("name"));
        assertEquals("REMOVE-ME", props.getEntries().get("removed"));
    }

    private enum TestEnum {
        FIRST, SECOND
    }
}
