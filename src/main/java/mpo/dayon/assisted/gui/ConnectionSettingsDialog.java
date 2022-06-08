package mpo.dayon.assisted.gui;

import mpo.dayon.assisted.network.NetworkAssistedEngineConfiguration;
import mpo.dayon.common.gui.common.ImageNames;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static mpo.dayon.common.babylon.Babylon.translate;
import static mpo.dayon.common.gui.common.ImageUtilities.getOrCreateIcon;

public class ConnectionSettingsDialog {

    private final JTabbedPane tabbedPane;

    private final JTextField assistantIpAddressTextField;

    private final JTextField assistantPortNumberTextField;

    private final JTextField assistantTokenTextField;

    public ConnectionSettingsDialog(NetworkAssistedEngineConfiguration configuration) {

        JPanel connectionSettingsDialog = new JPanel(new GridLayout(2, 2, 10, 10));
        connectionSettingsDialog.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));

        final JLabel assistantIpAddress = new JLabel(translate("connection.settings.assistantIpAddress"));
        assistantIpAddressTextField = new JTextField();
        assistantIpAddressTextField.setText(configuration.getServerName());
        assistantIpAddressTextField.addMouseListener(clearTextOnDoubleClick(assistantIpAddressTextField));
        connectionSettingsDialog.add(assistantIpAddress);
        connectionSettingsDialog.add(assistantIpAddressTextField);

        final JLabel assistantPortNumber = new JLabel(translate("connection.settings.assistantPortNumber"));
        assistantPortNumberTextField = new JTextField();
        assistantPortNumberTextField.setText(String.valueOf(configuration.getServerPort()));
        assistantPortNumberTextField.addMouseListener(clearTextOnDoubleClick(assistantPortNumberTextField));
        connectionSettingsDialog.add(assistantPortNumber);
        connectionSettingsDialog.add(assistantPortNumberTextField);

        JPanel connectionTokenDialog = new JPanel(new GridLayout(1, 2, 10, 10));
        connectionTokenDialog.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));

        final JLabel assistantToken = new JLabel(getOrCreateIcon(ImageNames.KEY));
        assistantTokenTextField = new JTextField("", 7);
        assistantTokenTextField.setFont(new Font("Sans Serif", Font.PLAIN, 26));
        assistantTokenTextField.addMouseListener(clearTextOnDoubleClick(assistantTokenTextField));
        connectionTokenDialog.add(assistantToken);
        connectionTokenDialog.add(assistantTokenTextField);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab(translate("connection.settings.token"), connectionTokenDialog);
        tabbedPane.addTab("   ...   ", connectionSettingsDialog);

    }

    public String getIpAddress() {
        return this.assistantIpAddressTextField.getText();
    }

    public String getPortNumber() {
        return this.assistantPortNumberTextField.getText();
    }

    public String getToken() {
        return this.assistantTokenTextField.getText();
    }

    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    private MouseAdapter clearTextOnDoubleClick(JTextField textField) {
        return new Cleanser(textField);
    }

    private static class Cleanser extends MouseAdapter {
        private final JTextField textField;

        private Cleanser(JTextField textField) {
            this.textField = textField;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                textField.setText(null);
            }
        }
    }
}
