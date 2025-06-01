package mpo.dayon.common;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/*
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.DtlsFingerprintPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
*/

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.RemoteCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;

/**
 *
 * @author johnmichaelreed2
 */
public class ICE4J {

    int CURRENT_COMPONENT_PORT = 5000;
    int MIN_COMPONENT_PORT = 5000;
    int MAX_COMPONENT_PORT = 5100;

    /**
     * Here is an example of a function creating an Agent containing a
     * IceMediaStream for each stream name in the stream name list, and 2
     * Component for each of them (one for RTP, one for RTCP) but without their
     * remote candidates. This Agent will also use the STUN server given as
     * arguments to the function
     *
     * @param mediaNameSet - Stream names, ex. VIDEO and AUDIO
     * @param stunAddresses - Stun server names, ex. {"jitsi.org",
     * "numb.viagenie.ca", "stun.ekiga.net"}
     * @param turnAddresses
     * @return Agent - As defined in RFC 3264, an agent is the protocol
     * implementation involved in the offer/answer exchange. There are two
     * agents involved in an offer/answer exchange.
     * @throws IOException
     */
    public Agent generateIceMediaStream(
            Set<String> mediaNameSet,
            TransportAddress stunAddresses[])
            throws IOException {
        Agent agent = new Agent();
        //This agent isn't controlling this ICE session (responder side)
        agent.setControlling(false);

        IceMediaStream stream = null; // cannot find class IceMediaStream in location ICE4J - never used

        /* We add STUN and TURN addresses as StunCandidateHarverster/TurnCandidateHarvester
         * to the agent
         */
        if (stunAddresses != null) {
            for (TransportAddress stunAddress : stunAddresses) {
                agent.addCandidateHarvester(
                        new StunCandidateHarvester(stunAddress));
            }
        }
        /*
         * MAX_COMPONENT_PORT and MIN_COMPONENT_PORT are class attributes
         * giving the maximum and minimum value the port of the Component.
         * CURRENT_COMPONENT_PORT give the current minimum value for the port
         * with CURRENT_COMPONENT_PORT+50 being the current maximum
         */
        for (String name : mediaNameSet) {
            stream = agent.createMediaStream(name);

            if ((CURRENT_COMPONENT_PORT + 1) >= MAX_COMPONENT_PORT) {
                CURRENT_COMPONENT_PORT = MIN_COMPONENT_PORT;
            }
            agent.createComponent(
                    stream, //agent.createMediaStream("audio")
                    Transport.UDP,
                    CURRENT_COMPONENT_PORT,
                    CURRENT_COMPONENT_PORT,
                    CURRENT_COMPONENT_PORT + 50);
            // The three last arguments are: preferredPort, minPort, maxPort
            agent.createComponent(
                    stream,
                    Transport.UDP,
                    CURRENT_COMPONENT_PORT + 1,
                    CURRENT_COMPONENT_PORT + 1,
                    CURRENT_COMPONENT_PORT + 50);
            CURRENT_COMPONENT_PORT += 50;
        }

        return agent;
    }

    /*You then have to give to the remote peer your local transport candidate,
     either in the Offer or the Answer (depending if you are the initiator or not).
     Here is an example on what information you have to give/how you can get access to them : */
    public static void addLocalCandidateToContentList(
            Agent agent,
            Collection<ContentPacketExtension> contentList) {
        IceMediaStream iceMediaStream = null;
        // These are jingle classes.
        IceUdpTransportPacketExtension transport = null;
        DtlsFingerprintPacketExtension fingerprint = null;
        CandidatePacketExtension candidate = null;
        long candidateID = 0;

        //the contentList is a collection of ContentPacketExtension describing a content/stream :
        //the media part (possible codecs, options...) and its corresponding transport candidate
        for (ContentPacketExtension content : contentList) {
            //IceUdpTransportPacketExtension is a class describing the transport for a media
            //You can add CandidatePacketExtension to it for each local candidate
            transport = new IceUdpTransportPacketExtension();

            //the name of the content (for example "audio") is the name of the stream and IceMediaStream
            iceMediaStream = agent.getStream(content.getName());

            //Set the passwd and ufrag for turn/stun
            //THIS TWO LINES ARE IMPORTANT, YOU DON'T WANT TO SKIP THEM
            transport.setPassword(agent.getLocalPassword());
            transport.setUfrag(agent.getLocalUfrag());

            if (iceMediaStream != null) {
                for (Component component : iceMediaStream.getComponents()) {
                    for (LocalCandidate localCandidate : component.getLocalCandidates()) {
                        candidate = new CandidatePacketExtension();

                        //THIS PART IS THE MOST IMPORTANT IF YOU WANT TO KNOW WHAT YOU HAVE TO ADVERTISE
                        candidate.setNamespace(IceUdpTransportPacketExtension.NAMESPACE);
                        candidate.setFoundation(localCandidate.getFoundation());
                        candidate.setComponent(localCandidate.getParentComponent().getComponentID());
                        /* COMPILE ERROR HERE ON "getTransport()" - cannot find symbol/method */
                        // candidate.setProtocol(localCandidate.getParentComponent().getTransport().toString());

                        // Try this instead:
                        candidate.setProtocol(localCandidate.getTransportAddress().getTransport().toString());


                        candidate.setPriority(localCandidate.getPriority());
                        candidate.setIP(localCandidate.getTransportAddress().getHostAddress());
                        candidate.setPort(localCandidate.getTransportAddress().getPort());
                        candidate.setType(CandidateType.valueOf(localCandidate.getType().toString())); // Jingle or ice candidate type?
                        candidate.setGeneration(agent.getGeneration());
                        //if you need to differentiate the network controller used for ICE, you can set
                        //its number here. If not, 0 is the default value to set.
                        candidate.setNetwork(0);
                        //An unique identifier is required for each candidate
                        candidate.setID(String.valueOf(candidateID++));
                        if (localCandidate.getRelatedAddress() != null) {
                            candidate.setRelAddr(localCandidate.getRelatedAddress().getHostAddress());
                            candidate.setRelPort(localCandidate.getRelatedAddress().getPort());
                        }

                        transport.addCandidate(candidate);
                    }
                }
            }

            // cannot find symbol packet extension
            content.addChildExtension(transport); // incompatible types: IceUdpTransportPacketExtension cannot be converted to PacketExtension
        }
    }

    /*You then have to add the remote transport candidates to
     each of their corresponding IceMediaStream and Component
     (in this scenario, the remote candidates where given in
     ContentPacketExtension (see Jingle protocol)) : */
    public static void addRemoteCandidateToAgent(
            Agent agent,
            Collection<ContentPacketExtension> contentList) {
        IceUdpTransportPacketExtension transports = null;
        List<CandidatePacketExtension> candidates = null;
        String contentName = null;
        IceMediaStream stream = null;
        Component component = null;

        RemoteCandidate relatedCandidate = null;
        TransportAddress mainAddr = null, relatedAddr = null;
        RemoteCandidate remoteCandidate;

        for (ContentPacketExtension content : contentList) {
            contentName = content.getName();
            stream = agent.getStream(contentName);

            if (stream != null) {
                //We just get the first IceUdpTransportPacketExtension of the content (and not a list)
                transports = content.getFirstChildOfType(IceUdpTransportPacketExtension.class);

                stream.setRemotePassword(transports.getPassword());
                stream.setRemoteUfrag(transports.getUfrag());

                //We then get the list of all remote transport candidate of the IceUdpTransport packet
                candidates = transports.getChildExtensionsOfType(CandidatePacketExtension.class);
                //the candidates are sorted to be added to the Agent in the correct order
                Collections.sort(candidates);

                for (CandidatePacketExtension candidate : candidates) {
                    component = stream.getComponent(candidate.getComponent());
                    //The component of the remote candidate must correspond to a local component.
                    //The remote generation must be the same as the local generation
                    if ((component != null) && (candidate.getGeneration() == agent.getGeneration())) {
                        if ((candidate.getIP() != null) && (candidate.getPort() > 0)) {
                            mainAddr = new TransportAddress(
                                    candidate.getIP(),
                                    candidate.getPort(),
                                    Transport.parse(candidate.getProtocol().toLowerCase()));

                            relatedCandidate = null;
                            if ((candidate.getRelAddr() != null) && (candidate.getRelPort() > 0)) {
                                relatedAddr = new TransportAddress(
                                        candidate.getRelAddr(),
                                        candidate.getRelPort(),
                                        Transport.parse(candidate.getProtocol().toLowerCase()));
                                relatedCandidate = component.findRemoteCandidate(relatedAddr);
                            }

                            remoteCandidate = new RemoteCandidate(
                                    mainAddr,
                                    component,
                                    org.ice4j.ice.CandidateType.parse(candidate.getType().toString()),
                                    candidate.getFoundation(),
                                    candidate.getPriority(),
                                    relatedCandidate);

                            component.addRemoteCandidate(remoteCandidate);
                        }
                    }
                }
            }
        }
    }

    /* An all you have to do now is starting the Agent, and
     when ICE is completed, you can use the sockets the
     Agent has created : */
    public void main2() throws Exception {
        final Set mediaNameSet = new TreeSet();
        mediaNameSet.add("audio");
        mediaNameSet.add("video");
        final TransportAddress[] stunAddresses = new TransportAddress[2];
        stunAddresses[0] = new TransportAddress("stun.services.mozilla.com", 3478, Transport.UDP);
        stunAddresses[1] = new TransportAddress("stun.ekiga.net", 3478, Transport.UDP);
        final Agent agent = generateIceMediaStream(mediaNameSet, stunAddresses);
        final ContentPacketExtension audio_session_description = new ContentPacketExtension(
                ContentPacketExtension.CreatorEnum.initiator, "session", "audio", ContentPacketExtension.SendersEnum.both);
        final ContentPacketExtension video_session_description = new ContentPacketExtension(
                ContentPacketExtension.CreatorEnum.initiator, "session", "video", ContentPacketExtension.SendersEnum.both);
        final LinkedList contentList = new LinkedList();
        contentList.add(audio_session_description);
        contentList.add(video_session_description);
        addLocalCandidateToContentList(agent, contentList);
        addRemoteCandidateToAgent(agent, contentList); // is this really supposed to be the same contentList? At what point do we receive the information (CandidatePacketExtension?) about the remote from the STUN server?
        //Start the ICE process
        agent.startConnectivityEstablishment();

        //Running the ICE process doesn't block the tread, so you can do whatever you want until it's terminated,
        //but you mustn't use the sockets the Agent created, not before ICE terminates.
        //Here I decide to wait sleep until ICE terminates.
        while (IceProcessingState.TERMINATED != agent.getState()) {
            System.out.println("Connectivity Establishment in process");
            try {
                Thread.sleep(1500);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String streamName = "audio";
        IceMediaStream iceMediaStream = agent.getStream(streamName);
        CandidatePair rtpPair = iceMediaStream.getComponent(Component.RTP).getSelectedPair();
        CandidatePair rtcpPair = iceMediaStream.getComponent(Component.RTCP).getSelectedPair();
        DatagramSocket rtpSocket = rtpPair.getLocalCandidate().getDatagramSocket();
        DatagramSocket rtcpSocket = rtcpPair.getLocalCandidate().getDatagramSocket();
        // …
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception, Throwable {
        // TODO code application logic here

        //The best way to understand ICE4J is to look at some code
        final Agent agent = new Agent(); // A simple ICE Agent
        Application.check(agent != null);
        /**
         * * Setup the STUN servers: **
         */
        final String[] hostnames = new String[]{"jitsi.org", "numb.viagenie.ca", "stun.ekiga.net"};
        Application.check(hostnames != null);
        // Look online for actively working public STUN Servers. You can find free servers.
        // Now add these URLS as Stun Servers with standard 3478 port for STUN servrs.
        for (final String hostname : hostnames) {
            Application.check(hostname != null);
            try {
                // InetAddress qualifies a url to an IP Address, if you have an error here, make sure the url is reachable and correct
                final TransportAddress transport_address = new TransportAddress(InetAddress.getByName(hostname), 3478, Transport.UDP);
                Application.check(transport_address != null);
                // Currently Ice4J only supports UDP and will throw an Error otherwise
                agent.addCandidateHarvester(new StunCandidateHarvester(transport_address));
            } catch (Exception e) {
                e.printStackTrace();
                Application.check(false);
            }
        }
        // Now you have your Agent setup. The agent will now be able to know its IP Address
        // and Port once you attempt to connect. You do need to setup Streams on
        // the Agent to open a flow of information on a specific port.
        final IceMediaStream stream = agent.createMediaStream("audio");
        Application.check(stream != null);
        final int port = 5000; // Choose any port
        // The three last arguments are: preferredPort, minPort, maxPort
        agent.createComponent(stream, Transport.UDP, port, port, port + 100);

        /*Now we have our port and we have our stream to allow for information to
         flow. The issue is that once we have all the information we need each computer
         to get the remote computer's information. Of course how do you get that
         information if you can't connect? There might be a few ways, but the easiest
         with just ICE4J is to POST the information to your public sever and retrieve
         the information. I even use a simple PHP server I wrote to store and spit out
         information.*/
        // What information and how do you re-construct it?
        // Well we can borrow some code from test.SdpUtils:
        final String toSend = SdpUtils.createSDPDescription(agent);  //Each computer sends this information
        // This information describes all the possible IP addresses and ports
        Application.check(toSend != null);

        /* The String "toSend" should be sent to a server. You need
         to write a PHP, Java or any server. It should be able to have this
         String posted to a database. Each program checks to see if another
         program is requesting a call. If it is, they can both post this "toSend"
         information and then read eachother's "toSend" SDP string. After you
         get this information about the remote computer do the following for ice4j
         to build the connection: */
        String remoteReceived = ""; // This information was grabbed from the server, and shouldn't be empty.
        SdpUtils.parseSDP(agent, toSend); // ????
        // This will add the remote information to the agent.
        //SdpUtils.parseSDP(agent, remoteReceived); // Exception in thread "main" java.lang.NullPointerException on second parameter.
        //Application.check(false);

        //Hopefully now your Agent is totally setup. Now we need to start the connections:
        agent.addStateChangeListener(new StateListener()); // We will define this class soon
        // You need to listen for state change so that once connected you can then use the socket.
        agent.startConnectivityEstablishment(); // This will do all the work for you to connect

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
                                Application.check(false);
                            }
                        }
                    }
                }
            }
        }
    }
}