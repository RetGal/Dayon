package mpo.dayon.common;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.*;
import org.ice4j.ice.harvest.StunCandidateHarvester;

public class IceTest {

    public static void main(String[] args) throws Exception {
        Agent agent = new Agent(); // A simple ICE Agent

        /*** Setup the STUN servers: ***/
        String[] hostnames = new String[]{"jitsi.org", "numb.viagenie.ca", "stun.ekiga.net"};
        // Look online for actively working public STUN Servers. You can find
        // free servers.
        // Now add these URLS as Stun Servers with standard 3478 port for STUN
        // servrs.
        for (String hostname : hostnames) {
            try {
                // InetAddress qualifies a url to an IP Address, if you have an
                // error here, make sure the url is reachable and correct
                TransportAddress ta = new TransportAddress(InetAddress.getByName(hostname), 3478, Transport.UDP);
                // Currently Ice4J only supports UDP and will throw an Error
                // otherwise
                agent.addCandidateHarvester(new StunCandidateHarvester(ta));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /*
         * Now you have your Agent setup. The agent will now be able to know its
         * IP Address and Port once you attempt to connect. You do need to setup
         * Streams on the Agent to open a flow of information on a specific
         * port.
         */
        IceMediaStream stream = agent.createMediaStream("audio");
        int port = 5000; // Choose any port
        try {
            //agent.createComponent(stream, Transport.UDP, port, port, port + 100);
            agent.createComponent(stream, port, port, port + 100, KeepAliveStrategy.SELECTED_AND_TCP);
            // The three last arguments are: preferredPort, minPort, maxPort
        } catch (BindException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        /*
         * Now we have our port and we have our stream to allow for information
         * to flow. The issue is that once we have all the information we need
         * each computer to get the remote computer's information. Of course how
         * do you get that information if you can't connect? There might be a
         * few ways, but the easiest with just ICE4J is to POST the information
         * to your public sever and retrieve the information. I even use a
         * simple PHP server I wrote to store and spit out information.
         */
        String toSend = null;
        try {
            toSend = SdpUtils.createSDPDescription(agent);
            // Each computersends this information
            // This information describes all the possible IP addresses and
            // ports
        } catch (Throwable e) {
            e.printStackTrace();
        }

        /*The String "toSend" should be sent to a server. You need to write a PHP, Java or any server.
         * It should be able to have this String posted to a database.
         * Each program checks to see if another program is requesting a call.
         * If it is, they can both post this "toSend" information and then read eachother's "toSend" SDP string.
         * After you get this information about the remote computer do the following for ice4j to build the connection:*/

        String remoteReceived = toSend; // This information was grabbed from the server, and shouldn't be empty.
        SdpUtils.parseSDP(agent, remoteReceived); // This will add the remote information to the agent.
        //Hopefully now your Agent is totally setup. Now we need to start the connections:

        agent.addStateChangeListener(new StateListener()); // We will define this class soon
        // You need to listen for state change so that once connected you can then use the socket.
        agent.startConnectivityEstablishment(); // This will do all the work for you to connect
    }

    private static String toBase64(String toSend) {
        return Base64.getEncoder().encodeToString(toSend.getBytes(StandardCharsets.UTF_8));
    }

    public static class StateListener implements PropertyChangeListener {

        private InetAddress hostname;
        int port;

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getSource() instanceof Agent) {
                Agent agent = (Agent) evt.getSource();
                if (agent.getState().equals(IceProcessingState.TERMINATED)) {
                    // Your agent is connected. Terminated means ready to communicate
                    for (IceMediaStream stream : agent.getStreams()) {
                        if (stream.getName().contains("audio")) {
                            Component rtpComponent = stream.getComponent(org.ice4j.ice.Component.RTP);
                            CandidatePair rtpPair = rtpComponent.getSelectedPair();
                            // We use IceSocketWrapper, but you can just use the UDP socket
                            // The advantage is that you can change the protocol from UDP to TCP easily
                            // Currently only UDP exists so you might not need to use the UDP_socket.
                            DatagramSocket UDP_socket = rtpPair.getIceSocketWrapper().getUDPSocket();
                            //Socket tcpSocket = rtpPair.getIceSocketWrapper().getTCPSocket();
                            // Get information about remote address for packet settings
                            TransportAddress transport_address = rtpPair.getRemoteCandidate().getTransportAddress();
                            hostname = transport_address.getAddress();
                            port = transport_address.getPort();

                            try {
                                //Once you have the IceSocketWrapper or UDP Socket you can just send and receive as usual.
                                //When you send you do need to setup the correct hostname and ports within the packet as follows:
                                DatagramPacket packet = new DatagramPacket(new byte[10000], 10000);
                                packet.setAddress(hostname);
                                packet.setPort(port);
                                UDP_socket.send(packet);
                                //tcpSocket.connect(transport_address);

                                // Receiving information is easier, no address or port information is needed:
                                DatagramPacket receiving_packet = new DatagramPacket(new byte[10000], 10000);
                                UDP_socket.receive(receiving_packet); // This will block until you receive data that you can use.
                                /* It is a good idea to always try to keep both sides having the same
                                 length of byte array otherwise you might lose some data. This is because the
                                 whole UDP packet isn't being stored because it will overflow your array.

                                 You should know that in Java UDP packets are broken up and sent to the
                                 other computer where they are re-assembled. If one of the pieces are
                                 missing than the whole packet will be discarded.
                                 */
                            } catch (IOException e) {
                                // TODO
                                //Application.check(false);
                            }
                        }
                    }
                }
            }
        }
    }

}
