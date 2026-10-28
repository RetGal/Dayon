module mpo.dayon {
    requires ice4j;
    requires java.datatransfer;
    requires java.desktop;
    requires java.logging;
    requires java.net.http;
    requires java.sdp.nist.bridge;
    requires org.tukaani.xz;
    requires sdp.api;
    requires WaifUPnP;

    exports mpo.dayon.assistant;
    exports mpo.dayon.assisted;
    exports mpo.dayon.common;
}