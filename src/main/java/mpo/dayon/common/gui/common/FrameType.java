package mpo.dayon.common.gui.common;

public enum FrameType {
    ASSISTANT("assistant"), ASSISTED("assisted");

    private final String prefix;

    FrameType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
