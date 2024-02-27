package mpo.dayon.assistant.gui;

import javax.swing.Action;

class AssistantActions {

    private Action ipAddressAction;
    private Action networkConfigurationAction;
    private Action captureEngineConfigurationAction;
    private Action compressionEngineConfigurationAction;
    private Action resetAction;
    private Action remoteClipboardRequestAction;
    private Action remoteClipboardSetAction;
    private Action screenshotRequestAction;
    private Action startAction;
    private Action stopAction;
    private Action tokenAction;
    private Action toggleCompatibilityModeAction;

    Action getIpAddressAction() {
        return ipAddressAction;
    }

    void setIpAddressAction(Action ipAddressAction) {
        this.ipAddressAction = ipAddressAction;
    }

    Action getNetworkConfigurationAction() {
        return networkConfigurationAction;
    }

    void setNetworkConfigurationAction(Action networkConfigurationAction) {
        this.networkConfigurationAction = networkConfigurationAction;
    }

    Action getCaptureEngineConfigurationAction() {
        return captureEngineConfigurationAction;
    }

    void setCaptureEngineConfigurationAction(Action captureEngineConfigurationAction) {
        this.captureEngineConfigurationAction = captureEngineConfigurationAction;
    }

    Action getCompressionEngineConfigurationAction() {
        return compressionEngineConfigurationAction;
    }

    void setCompressionEngineConfigurationAction(Action compressionEngineConfigurationAction) {
        this.compressionEngineConfigurationAction = compressionEngineConfigurationAction;
    }

    Action getResetAction() {
        return resetAction;
    }

    void setResetAction(Action resetAction) {
        this.resetAction = resetAction;
    }


    Action getRemoteClipboardRequestAction() {
        return remoteClipboardRequestAction;
    }

    void setRemoteClipboardRequestAction(Action remoteClipboardRequestAction) {
        this.remoteClipboardRequestAction = remoteClipboardRequestAction;
    }

    Action getRemoteClipboardSetAction() {
        return remoteClipboardSetAction;
    }

    void setRemoteClipboardSetAction(Action remoteClipboardSetAction) {
        this.remoteClipboardSetAction = remoteClipboardSetAction;
    }

    Action getScreenshotRequestAction() {
        return screenshotRequestAction;
    }

    void setScreenshotRequestAction(Action screenshotRequestAction) {
        this.screenshotRequestAction = screenshotRequestAction;
    }

    Action getStartAction() {
        return startAction;
    }

    void setStartAction(Action startAction) {
        this.startAction = startAction;
    }

    Action getStopAction() {
        return stopAction;
    }

    void setStopAction(Action stopAction) {
        this.stopAction = stopAction;
    }

    Action getTokenAction() {
        return tokenAction;
    }

    void setTokenAction(Action tokenAction) {
        this.tokenAction = tokenAction;
    }

    Action getToggleCompatibilityModeAction() {
        return toggleCompatibilityModeAction;
    }

    void setToggleCompatibilityModeAction(Action toggleCompatibilityModeAction) {
        this.toggleCompatibilityModeAction = toggleCompatibilityModeAction;
    }
}
