package mpo.dayon.smoke;

import com.dosse.upnp.UPnP;
import mpo.dayon.common.network.SdpUtils;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Component;
import org.ice4j.ice.HostCandidate;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.KeepAliveStrategy;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Smoke test for the shaded jar.
 *
 * <p>{@code minimizeJar} removes everything that is not statically reachable from Dayon's
 * classes. Classes that are only loaded by name (kotlin-reflect, the NIST SDP parsers, ...)
 * are invisible to that analysis and are pinned by the keep filters in {@code pom.xml}.
 * This test exercises the runtime paths of the trimmed dependencies so a missing class
 * fails the build in the {@code verify} phase instead of in production.</p>
 *
 * <p>It is compiled and run by the JDK's single-file source launcher with only the shaded
 * jar on the classpath, so it must stay Java 11 compatible and may not depend on test
 * scope libraries.</p>
 *
 * <p>Host candidate harvesting is skipped on purpose: the test must also pass on machines
 * with only a loopback interface (like CI sandboxes), where ice4j's host harvester cannot
 * find a usable address. Components and host candidates are therefore created directly
 * (via reflection on the protected ice4j factory methods), which is enough to exercise
 * SDP creation and parsing.</p>
 */
public final class ShadedJarSmokeTest {

    private ShadedJarSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        // 1. Classes that are only looked up by name or referenced for their annotations and
        //    constants are invisible to minimizeJar's static analysis. This is the runtime
        //    counterpart of the keep filters in pom.xml: if one of them stops matching, the
        //    build fails here instead of at some later point on a user's machine.
        //    This has to run before anything touches the NIST SDP parsers: ParserFactory
        //    silently calls System.exit(0) when one of its classes cannot be loaded.
        for (String required : new String[]{
                "kotlin.reflect.jvm.internal.ReflectionFactoryImpl",
                "kotlin.io.CloseableKt",
                "kotlin.jvm.internal.LocalVariableReferencesKt",
                "kotlin.ExtensionFunctionType",
                "kotlin.ParameterName",
                "kotlin.internal.NoInfer",
                "gov.nist.core.HostNameParser",
                "gov.nist.core.HostPort",
                "gov.nist.core.Separators",
                "gov.nist.javax.sdp.fields.SDPKeywords",
                "gov.nist.javax.sdp.parser.OriginFieldParser",
                "gov.nist.javax.sdp.parser.AttributeFieldParser",
                "javax.sdp.SdpConstants"}) {
            Class.forName(required);
        }

        // 2. kotlin-reflect driven jitsi/ice4j configuration (AgentConfig, HarvestConfig, JitsiConfig)
        System.out.println("ice4j config: " + org.ice4j.ice.AgentConfig.config.getSoftware()
                + " / harvest timeout " + org.ice4j.ice.harvest.HarvestConfig.config.timeout());

        // 3. ICE agent and harvesters (their constructors pull in much of ice4j)
        Agent agent = new Agent();
        agent.setControlling(true);
        TransportAddress stun = new TransportAddress(
                new InetSocketAddress("meet-jit-si-turnrelay.jitsi.net", 3478), Transport.UDP);
        agent.addCandidateHarvester(new StunCandidateHarvester(stun));
        agent.addCandidateHarvester(new TurnCandidateHarvester(stun, new LongTermCredential("user", "pass")));

        IceMediaStream stream = agent.createMediaStream("data");
        addManualComponent(stream, 5000);

        // 4. jitsi logging used by ice4j
        org.jitsi.utils.logging2.Logger logger =
                new org.jitsi.utils.logging2.LoggerImpl("smoke",
                        new org.jitsi.utils.logging2.LogContext("case", "smoke"));
        logger.info("logger ok");

        // 5. SDP encode + parse (the NIST SDP parsers are loaded by name)
        String sdp = SdpUtils.createSDPDescription(agent);
        System.out.println("sdp heads: " + sdp.substring(0, Math.min(20, sdp.length())).replace('\n', ' '));
        Agent peer = new Agent();
        addManualComponent(peer.createMediaStream("data"), 5001);
        SdpUtils.parseSDP(peer, sdp);
        Component remote = peer.getStreams().iterator().next().getComponent(Component.RTP);
        System.out.println("sdp parsed, remote candidates: " + remote.getRemoteCandidateCount());
        if (remote.getRemoteCandidateCount() == 0) {
            throw new IllegalStateException("sdp parsing produced no remote candidates");
        }

        // 6. xz compress/decompress
        byte[] raw = new byte[64 * 1024];
        ThreadLocalRandom.current().nextBytes(raw);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (XZOutputStream out = new XZOutputStream(compressed, new LZMA2Options(3))) {
            out.write(raw);
        }
        try (XZInputStream in = new XZInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {
            byte[] back = in.readAllBytes();
            if (!Arrays.equals(raw, back)) {
                throw new IllegalStateException("xz round trip failed");
            }
        }

        // 7. UPnP gateway discovery (no mapping is created, the finder times out after ~3s)
        System.out.println("upnp available: " + UPnP.isUPnPAvailable());

        // 8. Dayon's own logging
        mpo.dayon.common.log.Log.info("dayon log ok");

        agent.free();
        peer.free();
        System.out.println("SMOKE TEST OK");
    }

    /**
     * Creates an RTP component and a loopback host candidate without running the harvesters.
     */
    private static Component addManualComponent(IceMediaStream stream, int port) throws Exception {
        Method create = IceMediaStream.class.getDeclaredMethod(
                "createComponent", KeepAliveStrategy.class, boolean.class);
        create.setAccessible(true);
        Component component = (Component) create.invoke(stream, KeepAliveStrategy.SELECTED_ONLY, false);

        HostCandidate candidate = new HostCandidate(
                new TransportAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), Transport.UDP),
                component);
        component.addLocalCandidate(candidate);

        Method select = Component.class.getDeclaredMethod("selectDefaultCandidate");
        select.setAccessible(true);
        select.invoke(component);
        return component;
    }
}
