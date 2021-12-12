package mpo.dayon.assisted.control;

import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import java.awt.*;
import java.awt.event.KeyEvent;

import static mpo.dayon.common.network.message.NetworkKeyControlMessage.KeyState.PRESSED;
import static mpo.dayon.common.network.message.NetworkKeyControlMessage.KeyState.RELEASED;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RobotNetworkControlMessageHandlerTest {

    Robot robot = mock(Robot.class);
    RobotNetworkControlMessageHandler controlMessageHandler = new RobotNetworkControlMessageHandler(robot);

    @Test
    void testHandleMessagePress0() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 48, '0');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(48);
    }

    @Test
    void testHandleMessagePressA() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 65, 'A');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(65);
    }

    @Test
    void testHandleMessagePressz() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 122, 'z');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(122);
    }

    @Test
    void testHandleMessageReleaseA() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(RELEASED, 65, 'A');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyRelease(65);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessagePressAe() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 0, 'ä');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(KeyEvent.VK_CONTROL);
        verify(robot).keyPress(KeyEvent.VK_SHIFT);
        verify(robot).keyPress(KeyEvent.VK_U);
        verify(robot).keyRelease(KeyEvent.VK_U);
        // 228 as hex E4
        verify(robot).keyPress(KeyEvent.VK_E);
        verify(robot).keyRelease(KeyEvent.VK_E);
        verify(robot).keyPress(KeyEvent.VK_4);
        verify(robot).keyRelease(KeyEvent.VK_4);
        verify(robot).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot).keyRelease(KeyEvent.VK_SHIFT);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessagePressAt() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 50, '@');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(50);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessagePressTurkishLowerSpecialSTypedOnNonTurkishAssisted() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 0, 'ş');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(KeyEvent.VK_CONTROL);
        verify(robot).keyPress(KeyEvent.VK_SHIFT);
        verify(robot).keyPress(KeyEvent.VK_U);
        verify(robot).keyRelease(KeyEvent.VK_U);
        // 351 as hex 15F
        verify(robot).keyPress(KeyEvent.VK_1);
        verify(robot).keyRelease(KeyEvent.VK_1);
        verify(robot).keyPress(KeyEvent.VK_5);
        verify(robot).keyRelease(KeyEvent.VK_5);
        verify(robot).keyPress(KeyEvent.VK_F);
        verify(robot).keyRelease(KeyEvent.VK_F);
        verify(robot).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot).keyRelease(KeyEvent.VK_SHIFT);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessagePressTurkishUpperSpecialSTypedOnNonTurkishAssisted() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, 0, 'Ş');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(KeyEvent.VK_CONTROL);
        verify(robot).keyPress(KeyEvent.VK_SHIFT);
        verify(robot).keyPress(KeyEvent.VK_U);
        verify(robot).keyRelease(KeyEvent.VK_U);
        // 350 as hex 15E
        verify(robot).keyPress(KeyEvent.VK_1);
        verify(robot).keyRelease(KeyEvent.VK_1);
        verify(robot).keyPress(KeyEvent.VK_5);
        verify(robot).keyRelease(KeyEvent.VK_5);
        verify(robot).keyPress(KeyEvent.VK_E);
        verify(robot).keyRelease(KeyEvent.VK_E);
        verify(robot).keyRelease(KeyEvent.VK_CONTROL);
        verify(robot).keyRelease(KeyEvent.VK_SHIFT);
    }
}