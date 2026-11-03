package mpo.dayon.common.network;

import org.ice4j.ice.Agent;
import org.ice4j.ice.IceMediaStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sdp.SdpException;

import static org.junit.jupiter.api.Assertions.*;

class SdpUtilsTest {

    @AfterEach
    void tearDown() {
        SdpUtils.releaseSdpFactory();
    }

    private static Agent agentWithStream() throws Exception {
        final Agent agent = new Agent();
        final IceMediaStream stream = agent.createMediaStream("audio");
        agent.createComponent(stream, 0, 0, 0);
        return agent;
    }

    @Test
    void createsAndParsesAnSdpDescription() throws Exception {
        // given
        final Agent agent = agentWithStream();

        // when
        final String sdp = SdpUtils.createSDPDescription(agent);

        // then
        assertNotNull(sdp);
        assertTrue(sdp.contains("audio"));
        assertTrue(sdp.contains("ice-ufrag"));
        assertTrue(sdp.contains("ice-pwd"));
    }

    @Test
    void parsesTheRemoteDescription() throws Exception {
        // given
        final Agent localAgent = agentWithStream();
        final Agent remoteAgent = agentWithStream();
        final String sdp = SdpUtils.createSDPDescription(remoteAgent);

        // when
        SdpUtils.parseSDP(localAgent, sdp);

        // then
        final IceMediaStream stream = localAgent.getStream("audio");
        assertNotNull(stream);
        assertNotNull(stream.getRemoteUfrag());
        assertNotNull(stream.getRemotePassword());
        assertNotNull(stream.getComponent(org.ice4j.ice.Component.RTP).getDefaultRemoteCandidate());
    }

    @Test
    void createSDPDescriptionRejectsAnAgentWithoutStreams() {
        // given
        final Agent agent = new Agent();

        // when, then
        assertThrows(SdpException.class, () -> SdpUtils.createSDPDescription(agent));
        assertThrows(SdpException.class, () -> SdpUtils.createSDPDescription(null));
    }

    @Test
    void parseSDPIgnoresUnknownStreams() throws Exception {
        // given
        final Agent localAgent = agentWithStream();
        final Agent remoteAgent = new Agent();
        final IceMediaStream video = remoteAgent.createMediaStream("video");
        remoteAgent.createComponent(video, 0, 0, 0);
        final String sdp = SdpUtils.createSDPDescription(remoteAgent);

        // when
        SdpUtils.parseSDP(localAgent, sdp);

        // then
        assertNotNull(localAgent.getStream("audio").getRemoteUfrag());
        assertNotNull(localAgent.getStream("audio").getRemotePassword());
        assertNull(localAgent.getStream("video"));
    }
}
