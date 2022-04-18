package mpo.dayon.assisted.gui;

import mpo.dayon.common.gui.common.ImageNames;
import mpo.dayon.common.gui.common.ImageUtilities;
import mpo.dayon.common.log.Log;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;
import static mpo.dayon.common.babylon.Babylon.translate;

class AssistedStartAction extends AbstractAction {
    private final transient Assisted assisted;

    AssistedStartAction(Assisted assisted) {
        this.assisted = assisted;

        putValue(Action.NAME, "start");
        putValue(Action.SHORT_DESCRIPTION, translate("connect.assistant"));
        putValue(Action.SMALL_ICON, ImageUtilities.getOrCreateIcon(ImageNames.START));
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        assisted.onReady();
        new NetWorker().execute();
    }

    class NetWorker extends SwingWorker<String, String> {
        @Override
        protected String doInBackground() {
            if (assisted.start() && !isCancelled()) {
                assisted.connect();
            }
            return null;
        }

        @Override
        protected void done() {
            try {
                if (!isCancelled()) {
                    super.get();
                    Log.debug(format("NetWorker is done [%s]", assisted.getConfiguration().getServerName()));
                }
            } catch (InterruptedException | ExecutionException ie) {
                Log.info("NetWorker was cancelled");
                Thread.currentThread().interrupt();
            }
        }
    }
}
