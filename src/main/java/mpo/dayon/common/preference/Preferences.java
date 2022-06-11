package mpo.dayon.common.preference;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;

public final class Preferences {
    private static final Preferences NULL = new Preferences();

    private static Preferences preferences;

    private final File file;

    private final Properties props;

    private final AtomicBoolean writeError = new AtomicBoolean();

    private final Object cloneLOCK = new Object();

    private final AtomicBoolean dirty = new AtomicBoolean();

    private Preferences() {
        this.file = null;
        this.props = new Properties();
    }

    private Preferences(File file) throws IOException {
        this.file = file;
        this.props = new Properties();

        if (file.exists()) {
            try (FileReader in = new FileReader(file)) {
                getProps().load(in);
            } catch (FileNotFoundException e) {
                Log.error("Preferences (read) permission denied");
            }
        }
        // otherwise, use default values - until a persist() // is done later ...
    }

    public boolean isNull() {
        return this == NULL;
    }

    public static synchronized Preferences getPreferences() {
        if (preferences != null) {
            return preferences;
        }

        final String name = System.getProperty("dayon.application.name");

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
            preferences = xpreferences;
            return preferences;
        } catch (IOException ex) {
            Log.warn("Preferences get/create error!", ex);
            preferences = NULL;
            return preferences;
        }
    }

    public String getStringPreference(String name, String defaultValue) {
        return SystemUtilities.getStringProperty(getProps(), name, defaultValue);
    }

    public int getIntPreference(String name, int defaultValue) {
        return SystemUtilities.getIntProperty(getProps(), name, defaultValue);
    }

    public <T extends Enum<T>> T getEnumPreference(String name, T defaultValue, T[] enums) {
        return SystemUtilities.getEnumProperty(getProps(), name, defaultValue, enums);
    }

    public double getDoublePreference(String name, double defaultValue) {
        return SystemUtilities.getDoubleProperty(getProps(), name, defaultValue);
    }

    public boolean getBooleanPreference(String name, boolean defaultValue) {
        return SystemUtilities.getBooleanProperty(getProps(), name, defaultValue);
    }

    private AtomicBoolean getWriteError() {
        return writeError;
    }

    private Object getCloneLOCK() {
        return cloneLOCK;
    }

    private AtomicBoolean getDirty() {
        return dirty;
    }

    private Properties getProps() {
        return props;
    }

    private File getFile() {
        return file;
    }

    public static class Props {
        private static final String REMOVE = "REMOVE-ME";

        private final Map<String, String> entries = new HashMap<>();

        public void set(String name, String value) {
            getEntries().put(name, value);
        }

        public void clear(String name) {
            getEntries().put(name, REMOVE);
        }

        public Map<String, String> getEntries() {
            return entries;
        }
    }

    /**
     * Called from multiple threads (!)
     */
    public void update(Props props) {
        synchronized (getCloneLOCK()) {
            props.getEntries().forEach((pname, pvalue) -> {
                if (Props.REMOVE.equals(pvalue)) {
                    this.getProps().remove(pname);
                } else {
                    this.getProps().setProperty(pname, pvalue);
                }
            });
            getDirty().set(true);
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
                if (preferences.isNull() || preferences.getWriteError().get()) {
                    return;
                }

                try {
                    Properties cloned = null;
                    synchronized (preferences.getCloneLOCK()) {
                        if (preferences.getDirty().get()) {
                            cloned = (Properties) preferences.getProps().clone();
                            preferences.getDirty().set(false);
                        }
                    }
                    if (cloned != null) {
                        Log.debug("Writing the preferences [" + preferences.getFile().getAbsolutePath() + "]");
                        try (PrintWriter out = new PrintWriter(preferences.getFile())) {
                            cloned.store(out, null);
                            out.flush();
                        }
                    }
                } catch (FileNotFoundException e) {
                    Log.error("Preferences (write) permission denied");
                    preferences.getWriteError().set(true);
                } catch (IOException ex) {
                    Log.error("Preferences write error!", ex);
                    preferences.getWriteError().set(true);
                }
            }
        }, 0, 2000);
    }
}
