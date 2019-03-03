package mpo.dayon.common.preference;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;

public class Preferences {
    private static final Preferences NULL = new Preferences();

    private static Preferences preferences;

    private final File file;

    private final Properties props;

    /**
     * No need trying to write always...
     */
    private final AtomicBoolean writeError = new AtomicBoolean();

    private final Object cloneLOCK = new Object();

    private boolean dirty;

    private Preferences() {
        this.file = null;
        this.props = new Properties();
    }

    private Preferences(File file) throws IOException {
        this.file = file;
        this.props = new Properties();

        if (file.exists()) // otherwise, use default values - until a persist() // is done later ...
        {
            try (FileReader in = new FileReader(file)) {
                props.load(in);
            }
        }
    }

    public boolean isNull() {
        return this == NULL;
    }

    public static synchronized Preferences getPreferences() {
        if (preferences != null) {
            return preferences;
        }

        final String name = SystemUtilities.getApplicationName();

        try {
            final Preferences xpreferences;

            final File file = SystemUtilities.getOrCreateAppFile(name + ".properties");

            if (file != null) {
                if (file.exists()) {
                    Log.info("Preferences (existing) [" + file.getAbsolutePath() + "]");
                } else {
                    Log.info("Preferences (new) [" + file.getAbsolutePath() + "]");
                }
                xpreferences = new Preferences(file);
            } else {
                Log.info("Preferences [null]");
                xpreferences = NULL;
            }

            setupPersister(xpreferences);

            return preferences = xpreferences;
        } catch (Exception ex) {
            Log.warn("Preferences get/create error!", ex);
            return preferences = NULL;
        }
    }

    public String getStringPreference(String name, String defaultValue) {
        return SystemUtilities.getStringProperty(props, name, defaultValue);
    }

    public int getIntPreference(String name, int defaultValue) {
        return SystemUtilities.getIntProperty(props, name, defaultValue);
    }

    public <T extends Enum<T>> T getEnumPreference(String name, T defaultValue, T[] enums) {
        return SystemUtilities.getEnumProperty(props, name, defaultValue, enums);
    }

    public double getDoublePreference(String name, double defaultValue) {
        return SystemUtilities.getDoubleProperty(props, name, defaultValue);
    }

    public boolean getBooleanPreference(String name, boolean defaultValue) {
        return SystemUtilities.getBooleanProperty(props, name, defaultValue);
    }

    public static class Props {
        private static final String REMOVE = "REMOVE-ME";

        private final Map<String, String> entries = new HashMap<>();

        public void set(String name, String value) {
            entries.put(name, value);
        }

        public void clear(String name) {
            entries.put(name, REMOVE);
        }
    }

    /**
     * Called from multiple threads (!)
     */
    public void update(Props props) {
        synchronized (cloneLOCK) {
            for (Map.Entry<String, String> entry : props.entries.entrySet()) {
                final String pname = entry.getKey();
                final String pvalue = entry.getValue();

                if (Props.REMOVE.equals(pvalue)) {
                    this.props.remove(pname);
                } else {
                    this.props.setProperty(pname, pvalue);
                }
            }

            dirty = true;
        }
    }

    /**
     * Some components are possibly sending a lot of updates (e.g., main frame
     * resize) and it makes no sense to write every changes as we want the last
     * one only => I'm polling instead of saving each time a value has changed
     * ...
     */
    private static void setupPersister(final Preferences preferences) {
        new Timer("PreferencesWriter").schedule(new TimerTask() {
            @Override
            public void run() {
                if (preferences.isNull()) {
                    return;
                }

                try {
                    Properties cloned = null;

                    synchronized (preferences.cloneLOCK) {
                        if (preferences.dirty) {
                            cloned = (Properties) preferences.props.clone();
                            preferences.dirty = false;
                        }
                    }

                    if (cloned != null) {
                        Log.info("Writing the preferences [" + preferences.file.getAbsolutePath() + "]...");
                        try (PrintWriter out = new PrintWriter(preferences.file)) {
                            cloned.store(out, null);
                            out.flush();
                        }
                    }

                } catch (IOException ex) {
                    Log.warn("Preferences write error!", ex);
                    preferences.writeError.set(true);
                }
            }
        }, 0, 2000);
    }
}
