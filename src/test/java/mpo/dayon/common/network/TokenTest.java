package mpo.dayon.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenTest {

    @Test
    void resetShouldResetAllItsValuesExceptTheQueryParams() {
        // given
        Token token = new Token("?token=%s");
        token.setTokenString("12345B");

        // when
        token.reset();

        // then
        assertNull(token.getPeerAddress());
        assertEquals(0, token.getPeerPort());
        assertEquals("?token=%s", token.getQueryParams());
        assertNull(token.getTokenString());
    }

    @Test
    void setTokenStringShouldResetAllItsValuesExceptTheQueryParams() {
        // given
        Token token = new Token("?token=%s");

        // when
        token.updateToken("85.85.85.85", 1234, "192.168.10.10", true, 8080);

        // then
        assertEquals("85.85.85.85", token.getPeerAddress());
        assertEquals(1234, token.getPeerPort());
        assertEquals("192.168.10.10", token.getPeerLocalAddress());
        assertTrue(token.isPeerAccessible());
        assertEquals(8080, token.getLocalPort());
        assertEquals("?token=%s", token.getQueryParams());
        assertNull(token.getTokenString());

        // when
        token.setTokenString("1234A");

        // then
        assertNull(token.getPeerAddress());
        assertEquals(0, token.getPeerPort());
        assertNull(token.isPeerAccessible());
        assertEquals(0, token.getLocalPort());
        assertEquals("?token=%s", token.getQueryParams());
        assertEquals("1234A", token.getTokenString());
    }
}