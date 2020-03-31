package mpo.dayon.common.utils;

public abstract class UnitUtilities {
    private static final String DEC_UNIT = "%.2f %s";

    public enum BitUnit {
        KBIT("Kbit", "kilo", Math.pow(10, 3)),
        MBIT("Mbit", "mega", Math.pow(10, 6)),
        GBIT("Gbit", "giga", Math.pow(10, 9)),
        TBIT("Tbit", "tera", Math.pow(10, 12)),
        PBIT("Pbit", "peta", Math.pow(10, 15)),
        EBIT("Ebit", "exa", Math.pow(10, 18)),
        ZBIT("Zbit", "zetta", Math.pow(10, 21)),
        YBIT("Ybit", "yotta", Math.pow(10, 24));

        private final String symbol;
        private final String name;
        private final double value;

        BitUnit(String symbol, String name, double value) {
            this.symbol = symbol;
            this.name = name;
            this.value = value;
        }
    }

    public static String toBitSize(double bits) {
        final BitUnit[] units = BitUnit.values();

        for (int idx = units.length - 1; idx >= 0; idx--) {
            final BitUnit unit = units[idx];

            if (bits >= unit.value) {
                return String.format(DEC_UNIT, bits / unit.value, unit.symbol);
            }
        }
        return String.format(DEC_UNIT, bits, "bit");
    }

    public enum ByteUnit {
        K("K", "kilo", Math.pow(2, 10), 0),
        M("M", "mega", Math.pow(2, 20),0),
        G("G", "giga", Math.pow(2, 30),1),
        T("T", "tera", Math.pow(2, 40),1),
        P("P", "peta", Math.pow(2, 50),2),
        E("E", "exa", Math.pow(2, 60),2),
        Z("Z", "zetta", Math.pow(2, 70), 3),
        Y("Y", "yotta", Math.pow(2, 80), 3);

        private final String symbol;
        private final String name;
        private final double value;
        private final int decimals;

        ByteUnit(String symbol, String name, double value, int decimals) {
            this.symbol = symbol;
            this.name = name;
            this.value = value;
            this.decimals = decimals;
        }
    }

    public static String toByteSize(double bytes) {
        return toByteSize(bytes, true);
    }

    static String toByteSize(double bytes, boolean withDecimal) {
        final ByteUnit[] units = ByteUnit.values();

        for (int idx = units.length - 1; idx >= 0; idx--) {
            final ByteUnit unit = units[idx];

            if (bytes >= unit.value) {
                if (withDecimal) {
                    return String.format(DEC_UNIT, bytes / unit.value, unit.symbol);
                }
                return String.format("%." + unit.decimals + "f %s", bytes / unit.value,  unit.symbol);
            }
        }

        if (withDecimal) {
            return String.format(DEC_UNIT, bytes, "");
        }
        return String.format("%.0f %s", bytes, "");
    }

    /**
     * Converts a time in milli-seconds into ms, s, m, h or d.
     */
    public static String toElapsedTime(long millis) {
        double secs = millis / 1000.0;

        if (secs < 10.0) {
            return String.format("%dms", millis);
        } else if (secs < 60) {
            return String.format("%.2fs", secs);
        } else if (secs < 3600) {
            // noinspection NumericCastThatLosesPrecision
            return String.format("%dm%02ds", (int) Math.floor(secs / 60.0), Math.round(secs) % 60);
        } else if (secs < 24 * 3600) {
            // noinspection NumericCastThatLosesPrecision
            return String.format("%dh%02dm%02ds", (int) Math.floor(secs / 3600.0), (int) Math.floor(secs / 60.0) % 60, Math.round(secs) % 60);
        }
        // noinspection NumericCastThatLosesPrecision
        return String.format("%dd%02dh%02dm%02ds", (int) Math.floor(secs / 3600.0 / 24.0), (int) Math.floor(secs / 3600.0) % 24,
                (int) Math.floor(secs / 60.0) % 60, Math.round(secs) % 60);
    }

    /**
     * Converts a time in nano-seconds into ms, s, m, h or d.
     */
    public static String toElapsedNanoTime(long nanos) {
        if (nanos < 1000) {
            return String.format("%dns", nanos);
        }
        if (nanos < 1000 * 1000) {
            return String.format("%dus", nanos / 1000);
        }
        if (nanos < 1000 * 1000 * 1000) {
            return String.format("%dms", nanos / 1000 / 1000);
        }
        return toElapsedTime(nanos / 1000 / 1000);
    }

}
