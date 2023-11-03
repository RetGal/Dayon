package mpo.dayon.common.gui.common;

public enum FrameType {
    ASSISTANT("assistant", 1024, 512),
    ASSISTED("assisted", 512, 256);

    private final String prefix;
    private final Integer minWidth;
    private final Integer minHeight;

    FrameType(String prefix, Integer minWidth, Integer minHeight) {
        this.prefix = prefix;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    public String getPrefix() {
        return prefix;
    }

    public Integer getMinWidth() {
        return minWidth;
    }

    public Integer getMinHeight() {
        return minHeight;
    }
}
