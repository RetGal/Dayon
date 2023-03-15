package mpo.dayon.assistant.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetworkUtilitiesTest {

    @Test
    void getInetAddresses() {
        // given
        String loopBack = "127.0.0.1";

        // when
        final List<String> inetAddresses = NetworkUtilities.getInetAddresses();

        // then
        assertFalse(inetAddresses.isEmpty());
        assertEquals(loopBack, inetAddresses.get(inetAddresses.size()-1).toString());
    }
}