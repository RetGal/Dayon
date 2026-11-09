/*
 * ice4j, the OpenSource Java Solution for NAT and Firewall Traversal.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mpo.dayon.common.network;

import java.util.*;

import javax.sdp.*;

import org.ice4j.*;
import org.ice4j.ice.*;
import org.ice4j.ice.sdp.*;
import org.opentelecoms.javax.sdp.*;

/**
 * Utilities for manipulating SDP. Some of the utilities in this method <b>do
 * not</b> try to act smart and make a lot of assumptions (e.g. at least one
 * media stream with at least one component) that may not always be true in real
 * life and lead to exceptions. Therefore, make sure you reread the code if
 * reusing it in an application. It should be fine for the purposes of our ice4j
 * examples though.
 *
 * @author Emil Ivov
 * <p>
 * Reformatted, refactored and improved by Reto Galante
 */
public class SdpUtils {

    private static final ThreadLocal<SdpFactory> SDP_FACTORY = ThreadLocal.withInitial(NistSdpFactory::new);

    private SdpUtils() {
        // This utility class should not be instantiated
    }

    /**
     * Creates a session description containing the streams from the specified
     * <tt>agent</tt> using dummy codecs. This method is unlikely to be of use
     * to integrating applications as they would likely just want to feed a
     * {@link MediaDescription} and have it populated with all the necessary
     * attributes.
     *
     * @param agent the {@link Agent} we'd like to generate.
     * @return a {@link SessionDescription} representing <tt>agent</tt>'s
     * streams.
     * @throws SdpException on rainy days
     */
    public static String createSDPDescription(Agent agent) throws SdpException {
        SdpFactory factory = SDP_FACTORY.get();
        SessionDescription sDesc= factory.createSessionDescription();

        if (agent == null || agent.getStreams() == null || agent.getStreams().isEmpty()) {
            throw new SdpException("Cannot create an SDP description for an agent with no streams");
        }
        IceSdpUtils.initSessionDescription(sDesc, agent);
        return sDesc.toString();
    }

    /**
     * Configures <tt>localAgent</tt> the remote peer streams, components,
     * and candidates specified in <tt>sdp</tt>
     *
     * @param localAgent the {@link Agent} that we'd like to configure.
     * @param sdp        the SDP string that the remote peer sent.
     * @throws SdpException for all sorts of reasons.
     */
    @SuppressWarnings("unchecked") // jain-sdp legacy code.
    public static void parseSDP(Agent localAgent, String sdp) throws SdpException {
        SdpFactory factory = SDP_FACTORY.get();
        SessionDescription sDesc = factory.createSessionDescription(sdp);

        for (IceMediaStream stream : localAgent.getStreams()) {
            stream.setRemotePassword(sDesc.getAttribute("ice-pwd"));
            stream.setRemoteUfrag(sDesc.getAttribute("ice-ufrag"));
        }

        Connection globalConn = sDesc.getConnection();
        String globalConnAddr = globalConn != null ? globalConn.getAddress() : null;

        List<MediaDescription> mediaDescriptions = sDesc.getMediaDescriptions(true);
        for (MediaDescription desc : mediaDescriptions) {
            String streamName = desc.getMedia().getMediaType();
            IceMediaStream stream = localAgent.getStream(streamName);

            if (stream == null)
                continue;

            List<Attribute> attributes = desc.getAttributes(true);
            for (Attribute attribute : attributes) {
                if (attribute.getName().equals(CandidateAttribute.NAME))
                    parseCandidate(attribute, stream);
            }
            setDefaultCandidates(desc, globalConnAddr, stream);
        }
    }

    private static void setDefaultCandidates(MediaDescription desc, String globalConnAddr, IceMediaStream stream) throws SdpParseException {
        Connection streamConn = desc.getConnection();
        String streamConnAddr = streamConn != null ? streamConn.getAddress() : globalConnAddr;
        if (streamConnAddr == null) {
            // without a connection address there is nothing we can use as a default candidate (the explicit a=candidate lines still work)
            return;
        }

        int port = desc.getMedia().getMediaPort();
        TransportAddress defaultRtpAddress = new TransportAddress(streamConnAddr, port, Transport.UDP);

        int rtcpPort = desc.getAttribute("rtcp") != null ? Integer.parseInt(desc.getAttribute("rtcp")) : port + 1;
        TransportAddress defaultRtcpAddress = new TransportAddress(streamConnAddr, rtcpPort, Transport.UDP);

        Component rtpComponent = stream.getComponent(Component.RTP);
        Component rtcpComponent = stream.getComponent(Component.RTCP);

        if (rtpComponent != null) {
            Candidate<?> defaultRtpCandidate = rtpComponent.findRemoteCandidate(defaultRtpAddress);
            rtpComponent.setDefaultRemoteCandidate(defaultRtpCandidate);
        }

        if (rtcpComponent != null) {
            Candidate<?> defaultRtcpCandidate = rtcpComponent.findRemoteCandidate(defaultRtcpAddress);
            rtcpComponent.setDefaultRemoteCandidate(defaultRtcpCandidate);
        }
    }

    /**
     * Parses the <tt>attribute</tt>.
     *
     * @param attribute the attribute that we need to parse.
     * @param stream    the {@link IceMediaStream} that the candidate is supposed
     *                  to belong to.
     * @return a newly created {@link RemoteCandidate} matching the
     * content of the specified <tt>attribute</tt> or <tt>null</tt> if the
     * candidate belonged to a component we don't have.
     */
    private static RemoteCandidate parseCandidate(Attribute attribute, IceMediaStream stream) {
        final String value;
        try {
            value = attribute.getValue();
        } catch (SdpException e) {
            return null; // should never happen
        }
        if (value == null) {
            return null;
        }

        final String[] tokens = value.trim().split("\\s+");
        // foundation component transport priority address port typ type
        if (tokens.length < 8 || !"typ".equals(tokens[6])) {
            return null;
        }

        try {
            String foundation = tokens[0];
            int componentID = Integer.parseInt(tokens[1]);
            Transport transport = Transport.parse(tokens[2].toLowerCase(Locale.ROOT));
            long priority = Long.parseLong(tokens[3]);
            String address = tokens[4];
            int port = Integer.parseInt(tokens[5]);
            CandidateType type = CandidateType.parse(tokens[7]);

            Component component = stream.getComponent(componentID);
            if (component == null)
                return null;

            // raddr/rport are optional and may be followed by extension
            // attributes such as generation, network-id or ufrag.
            String relatedAddr = null;
            int relatedPort = 0;
            for (int i = 8; i + 1 < tokens.length; i += 2) {
                if ("raddr".equalsIgnoreCase(tokens[i])) {
                    relatedAddr = tokens[i + 1];
                } else if ("rport".equalsIgnoreCase(tokens[i])) {
                    relatedPort = Integer.parseInt(tokens[i + 1]);
                }
            }

            RemoteCandidate relatedCandidate = null;
            if (relatedAddr != null && relatedPort > 0) {
                TransportAddress raddr = new TransportAddress(relatedAddr, relatedPort, transport);
                relatedCandidate = component.findRemoteCandidate(raddr);
            }

            TransportAddress transAddr = new TransportAddress(address, port, transport);
            RemoteCandidate cand = new RemoteCandidate(transAddr, component, type, foundation, priority, relatedCandidate);
            component.addRemoteCandidate(cand);
            return cand;
        } catch (RuntimeException e) {
            // ignore a single malformed candidate instead of failing the complete connectivity establishment.
            return null;
        }
    }

    public static void releaseSdpFactory() {
        SDP_FACTORY.remove();
    }

}
