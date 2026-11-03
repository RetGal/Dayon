package mpo.dayon.common.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultThreadFactoryExTest {

    @Test
    void newThreadIsNamedAndNormalized() {
        // given
        DefaultThreadFactoryEx factory = new DefaultThreadFactoryEx("assistant");

        // when
        Thread thread = factory.newThread(() -> {
        });

        // then
        assertNotNull(thread);
        assertFalse(thread.isDaemon());
        assertEquals(Thread.NORM_PRIORITY, thread.getPriority());
        assertTrue(thread.getName().startsWith("assistant-"));
        assertEquals("assistant-", factory.newThread(() -> {
        }).getName().substring(0, "assistant-".length()));
    }

    @Test
    void newThreadUsesTheFactoryPrefix() throws InterruptedException {
        // given
        DefaultThreadFactoryEx factory = new DefaultThreadFactoryEx("assisted");
        Thread thread = factory.newThread(() -> {
        });

        // when
        thread.start();
        thread.join();

        // then
        assertTrue(thread.getName().startsWith("assisted-"));
        assertNotEquals("assisted-", thread.getName(), "a random number must be appended");
    }
}
