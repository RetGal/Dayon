package mpo.dayon.common.gui.common;

public enum FrameType {
    ASSISTANT("assistant", 640, 320),
    ASSISTED("assisted", 640, 60);

    private final String prefix;
    private final Integer minWidth;
    private final Integer minHeight;

    FrameType(String prefix, Integer minWidth, Integer minHeight) {
        this.prefix = prefix;
        this.minWidth = minWidth;
        if (System.getProperty("os.name").startsWith("Win")) {
            minHeight += 40;
        }
        this.minHeight = minHeight;
    }

    public String getPrefix() {
        return prefix;
    }

    Integer getMinWidth() {
        return minWidth;
    }

    Integer getMinHeight() {
        return minHeight;
    }
}
