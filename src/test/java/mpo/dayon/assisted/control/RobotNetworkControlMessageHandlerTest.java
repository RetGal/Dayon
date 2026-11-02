package mpo.dayon.assisted.control;

import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import java.awt.*;

import static java.awt.event.KeyEvent.*;
import static mpo.dayon.common.network.message.NetworkKeyControlMessage.KeyState.PRESSED;
import static mpo.dayon.common.network.message.NetworkKeyControlMessage.KeyState.RELEASED;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.mockito.Mockito.*;

class RobotNetworkControlMessageHandlerTest {

    private final Robot robot = mock(Robot.class);
    private final RobotNetworkControlMessageHandler controlMessageHandler = new RobotNetworkControlMessageHandler(robot);

    @Test
    void testHandleMessagePress0() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_0, '0');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(48);
    }

    @Test
    void testHandleMessagePressA() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_A, 'A');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(65);
    }

    @Test
    void testHandleMessagePress1() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_1, '1');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(49);
    }

    @Test
    void testHandleMessageDoNotReleaseUnpressedA() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(RELEASED, VK_A, 'A');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot, never()).keyRelease(65);
    }

    @Test
    void testHandleMessagePressAndReleaseB() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_B, 'B');
        controlMessageHandler.handleMessage(message);
        // when
        controlMessageHandler.handleMessage(new NetworkKeyControlMessage(RELEASED, VK_B, 'B'));
        // then
        verify(robot).keyRelease(66);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessagePressAeAsUnicode() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 0, 'ä');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(VK_CONTROL);
        verify(robot).keyPress(VK_SHIFT);
        verify(robot).keyPress(VK_U);
        verify(robot).keyRelease(VK_U);
        verify(robot).keyRelease(VK_CONTROL);
        verify(robot).keyRelease(VK_SHIFT);
        // 228 as hex E4
        verify(robot).keyPress(VK_E);
        verify(robot).keyRelease(VK_E);
        verify(robot).keyPress(VK_4);
        verify(robot).keyRelease(VK_4);
        verify(robot).keyPress(VK_ENTER);
        verify(robot).keyRelease(VK_ENTER);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessagePress6() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_6, '6');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(54);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessagePressTurkishLowerSpecialSTypedOnNonTurkishAssistedAsUnicode() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 0, 'ş');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(VK_CONTROL);
        verify(robot).keyPress(VK_SHIFT);
        verify(robot).keyPress(VK_U);
        verify(robot).keyRelease(VK_U);
        verify(robot).keyRelease(VK_CONTROL);
        verify(robot).keyRelease(VK_SHIFT);
        // 351 as hex 15F
        verify(robot).keyPress(VK_1);
        verify(robot).keyRelease(VK_1);
        verify(robot).keyPress(VK_5);
        verify(robot).keyRelease(VK_5);
        verify(robot).keyPress(VK_F);
        verify(robot).keyRelease(VK_F);
        verify(robot).keyPress(VK_ENTER);
        verify(robot).keyRelease(VK_ENTER);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessagePressTurkishUpperSpecialSTypedOnNonTurkishAssistedAsUnicode() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 0, 'Ş');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(VK_CONTROL);
        verify(robot).keyPress(VK_SHIFT);
        verify(robot).keyPress(VK_U);
        verify(robot).keyRelease(VK_U);
        verify(robot).keyRelease(VK_CONTROL);
        verify(robot).keyRelease(VK_SHIFT);
        // 350 as hex 15E
        verify(robot).keyPress(VK_1);
        verify(robot).keyRelease(VK_1);
        verify(robot).keyPress(VK_5);
        verify(robot).keyRelease(VK_5);
        verify(robot).keyPress(VK_E);
        verify(robot).keyRelease(VK_E);
        verify(robot).keyPress(VK_ENTER);
        verify(robot).keyRelease(VK_ENTER);
    }
}