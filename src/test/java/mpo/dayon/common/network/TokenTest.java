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
        assertEquals(0, token.getPort());
        assertEquals("?token=%s", token.getQueryParams());
        assertNull(token.getTokenString());
    }

    @Test
    void setTokenStringShouldResetAllItsValuesExceptTheQueryParams() {
        // given
        Token token = new Token("?token=%s");

        // when
        token.updateToken("127.0.0.1", 1234, true);

        // then
        assertEquals("127.0.0.1", token.getPeerAddress());
        assertEquals(1234, token.getPort());
        assertTrue(token.isPeerAccessible());
        assertEquals("?token=%s", token.getQueryParams());
        assertNull(token.getTokenString());

        // when
        token.setTokenString("1234A");

        // then
        assertNull(token.getPeerAddress());
        assertEquals(0, token.getPort());
        assertNull(token.isPeerAccessible());
        assertEquals("?token=%s", token.getQueryParams());
        assertEquals("1234A", token.getTokenString());
    }
}