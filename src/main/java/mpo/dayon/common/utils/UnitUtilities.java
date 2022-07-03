package mpo.dayon.common.utils;

import static java.lang.Math.pow;
import static java.lang.String.format;

public abstract class UnitUtilities {
    private static final String DEC_UNIT = "%.2f %s";

    public enum BitUnit {
        KBIT("Kbit", "kilo", pow(10, 3)),
        MBIT("Mbit", "mega", pow(10, 6)),
        GBIT("Gbit", "giga", pow(10, 9)),
        TBIT("Tbit", "tera", pow(10, 12)),
        PBIT("Pbit", "peta", pow(10, 15)),
        EBIT("Ebit", "exa", pow(10, 18)),
        ZBIT("Zbit", "zetta", pow(10, 21)),
        YBIT("Ybit", "yotta", pow(10, 24));

        private final String symbol;
        private final String name;
        private final double value;

        BitUnit(String symbol, String name, double value) {
            this.symbol = symbol;
            this.name = name;
            this.value = value;
        }

        double getValue() {
            return value;
        }

        String getSymbol() {
            return symbol;
        }
    }

    public static String toBitSize(double bits) {
        final BitUnit[] units = BitUnit.values();

        for (int idx = units.length - 1; idx >= 0; idx--) {
            final BitUnit unit = units[idx];

            if (bits >= unit.getValue()) {
                return format(DEC_UNIT, bits / unit.getValue(), unit.getSymbol());
            }
        }
        return format(DEC_UNIT, bits, "bit");
    }

    public enum ByteUnit {
        K("K", "kilo", pow(2, 10), "%.0f K"),
        M("M", "mega", pow(2, 20),"%.0f M"),
        G("G", "giga", pow(2, 30),"%.1f G"),
        T("T", "tera", pow(2, 40),"%.1f T"),
        P("P", "peta", pow(2, 50),"%.2f P"),
        E("E", "exa", pow(2, 60),"%.2f E"),
        Z("Z", "zetta", pow(2, 70), "%.3f Z"),
        Y("Y", "yotta", pow(2, 80), "%.3f Y");

        private final String symbol;
        private final String name;
        private final double value;
        private final String formatter;

        ByteUnit(String symbol, String name, double value, String formatter) {
            this.symbol = symbol;
            this.name = name;
            this.value = value;
            this.formatter = formatter;
        }

        double getValue() {
            return value;
        }

        String getSymbol() {
            return symbol;
        }

        String getFormatter() {
            return formatter;
        }
    }

    public static String toByteSize(double bytes) {
        return toByteSize(bytes, true);
    }

    static String toByteSize(double bytes, boolean withDecimal) {
        final ByteUnit[] units = ByteUnit.values();

        for (int idx = units.length - 1; idx >= 0; idx--) {
            final ByteUnit unit = units[idx];

            if (bytes >= unit.getValue()) {
                if (withDecimal) {
                    return format(DEC_UNIT, bytes / unit.getValue(), unit.getSymbol());
                }
                return format(unit.getFormatter(), bytes / unit.getValue());
            }
        }

        if (withDecimal) {
            return format(DEC_UNIT, bytes, "");
        }
        return format("%.0f %s", bytes, "");
    }

    /**
     * Converts a time in milli-seconds into ms, s, m, h or d.
     */
    public static String toElapsedTime(long millis) {
        double secs = millis / 1000.0;

        if (secs < 10.0) {
            return format("%dms", millis);
        }
        if (secs < 60) {
            return format("%.2fs", secs);
        }
        if (secs < 3600) {
            return format("%dm%02ds", toMinutes(secs), Math.round(secs) % 60);
        }
        if (secs < 86400) {
            return format("%dh%02dm%02ds", toHours(secs), toMinutes(secs) % 60, Math.round(secs) % 60);
        }
        // noinspection NumericCastThatLosesPrecision
        return format("%dd%02dh%02dm%02ds", (int) Math.floor(toHours(secs) / 24.0), toHours(secs) % 24,
                toMinutes(secs) % 60, Math.round(secs) % 60);
    }

    private static int toMinutes(double seconds) {
        // noinspection NumericCastThatLosesPrecision
        return (int) Math.floor(seconds / 60.0);
    }

    private static int toHours(double seconds) {
        // noinspection NumericCastThatLosesPrecision
        return (int) Math.floor(seconds / 3600.0);
    }

    /**
     * Converts a time in nano-seconds into ms, s, m, h or d.
     */
    public static String toElapsedNanoTime(long nanos) {
        if (nanos < 1000) {
            return format("%dns", nanos);
        }
        if (nanos < 1000 * 1000) {
            return format("%dus", nanos / 1000);
        }
        if (nanos < 1000 * 1000 * 1000) {
            return format("%dms", nanos / 1000 / 1000);
        }
        return toElapsedTime(nanos / 1000 / 1000);
    }

}
