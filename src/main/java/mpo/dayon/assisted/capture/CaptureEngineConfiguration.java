package mpo.dayon.assisted.capture;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.configuration.Configuration;
import mpo.dayon.common.preference.Preferences;

public class CaptureEngineConfiguration extends Configuration {
    private static final String PREF_VERSION = "assistant.capture.version";

    private static final String PREF_CAPTURE_TICK = "assistant.capture.tick";

    private static final String PREF_CAPTURE_QUANTIZATION = "assistant.capture.grayLevelQuantization";

    /**
     * A capture is performed every tick (millis).
     */
    private final int captureTick;

    /**
     * The actual number of gray levels.
     */
    private final Gray8Bits captureQuantization;

    /**
     * Default : takes its values from the current preferences.
     *
     * @see mpo.dayon.common.preference.Preferences
     */
    public CaptureEngineConfiguration() {
        final Preferences prefs = Preferences.getPreferences();
        captureTick = prefs.getIntPreference(PREF_CAPTURE_TICK, 200);
        captureQuantization = prefs.getEnumPreference(PREF_CAPTURE_QUANTIZATION, Gray8Bits.X_256, Gray8Bits.values());
    }

    public CaptureEngineConfiguration(int captureTick, Gray8Bits captureQuantization) {
        this.captureTick = captureTick;
        this.captureQuantization = captureQuantization;
    }

    public int getCaptureTick() {
        return captureTick;
    }

    public Gray8Bits getCaptureQuantization() {
        return captureQuantization;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CaptureEngineConfiguration that = (CaptureEngineConfiguration) o;

        return captureTick == that.getCaptureTick() && captureQuantization == that.getCaptureQuantization();
    }

    @Override
    public int hashCode() {
        return 31 * captureTick + (captureQuantization != null ? captureQuantization.hashCode() : 0);
    }

    /**
     * @param clear allows for clearing properties from previous version
     */
    @Override
    protected void persist(boolean clear) {
        final Preferences.Props props = getProps(clear);
        Preferences.getPreferences().update(props); // atomic (!)
    }

    private Preferences.Props getProps(boolean clear) {
        final Preferences.Props props = new Preferences.Props();
        props.set(PREF_VERSION, String.valueOf(1));
        props.set(PREF_CAPTURE_TICK, String.valueOf(captureTick));
        props.set(PREF_CAPTURE_QUANTIZATION, String.valueOf(captureQuantization.ordinal()));

        // migration support (!)
        if (clear) {
            props.clear("generations");
        }
        return props;
    }

    @Override
    public String toString() {
        return "[tick:" + captureTick + "][quantization:" + captureQuantization + "]";
    }
}
