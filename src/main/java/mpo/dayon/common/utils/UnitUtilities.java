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

        public double getValue() {
            return value;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public static String toBitSize(double bits) {
        final BitUnit[] units = BitUnit.values();

        for (int idx = units.length - 1; idx >= 0; idx--) {
            final BitUnit unit = units[idx];

            if (bits >= unit.getValue()) {
                return String.format(DEC_UNIT, bits / unit.getValue(), unit.getSymbol());
            }
        }
        return String.format(DEC_UNIT, bits, "bit");
    }

    public enum ByteUnit {
        K("K", "kilo", Math.pow(2, 10), "%.0f K"),
        M("M", "mega", Math.pow(2, 20),"%.0f M"),
        G("G", "giga", Math.pow(2, 30),"%.1f G"),
        T("T", "tera", Math.pow(2, 40),"%.1f T"),
        P("P", "peta", Math.pow(2, 50),"%.2f P"),
        E("E", "exa", Math.pow(2, 60),"%.2f E"),
        Z("Z", "zetta", Math.pow(2, 70), "%.3f Z"),
        Y("Y", "yotta", Math.pow(2, 80), "%.3f Y");

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

        public double getValue() {
            return value;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getFormatter() {
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
                    return String.format(DEC_UNIT, bytes / unit.getValue(), unit.getSymbol());
                }
                return String.format(unit.getFormatter(), bytes / unit.getValue());
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
        }
        if (secs < 60) {
            return String.format("%.2fs", secs);
        }
        if (secs < 3600) {
            return String.format("%dm%02ds", toMinutes(secs), Math.round(secs) % 60);
        }
        if (secs < 86400) {
            return String.format("%dh%02dm%02ds", toHours(secs), toMinutes(secs) % 60, Math.round(secs) % 60);
        }
        // noinspection NumericCastThatLosesPrecision
        return String.format("%dd%02dh%02dm%02ds", (int) Math.floor(toHours(secs) / 24.0), toHours(secs) % 24,
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
