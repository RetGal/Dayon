package mpo.dayon.assisted.control;

import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.mockito.InOrder;

import java.awt.*;
import java.awt.event.InputEvent;

import static java.awt.event.KeyEvent.*;
import static mpo.dayon.common.network.message.NetworkKeyControlMessage.KeyState.PRESSED;
import static mpo.dayon.common.network.message.NetworkKeyControlMessage.KeyState.RELEASED;
import static org.junit.jupiter.api.condition.OS.WINDOWS;
import static org.mockito.Mockito.*;

class RobotNetworkControlMessageHandlerTest {

    private final Robot robot = mock(Robot.class);
    private final RobotNetworkControlMessageHandler controlMessageHandler = new RobotNetworkControlMessageHandler(robot);

    @Test
    void testHandleMessageKeyPress0() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_0, '0');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(48);
    }

    @Test
    void testHandleMessageKeyPressA() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_A, 'A');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(65);
    }

    @Test
    void testHandleMessageKeyPress1() {
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
    void testHandleMessageKeyPressAndReleaseB() {
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
    void testHandleMessageKeyPressAeAsUnicode() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_UNDEFINED, 'ä');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        // 228 as hex E4 - 'E', '4' and finally ENTER are typed in that order
        InOrder inOrder = inOrder(robot);
        inOrder.verify(robot).keyPress(VK_CONTROL);
        inOrder.verify(robot).keyPress(VK_SHIFT);
        inOrder.verify(robot).keyPress(VK_U);
        inOrder.verify(robot).keyRelease(VK_U);
        inOrder.verify(robot).keyRelease(VK_SHIFT);
        inOrder.verify(robot).keyRelease(VK_CONTROL);
        inOrder.verify(robot).keyPress(VK_E);
        inOrder.verify(robot).keyRelease(VK_E);
        inOrder.verify(robot).keyPress(VK_4);
        inOrder.verify(robot).keyRelease(VK_4);
        inOrder.verify(robot).keyPress(VK_ENTER);
        inOrder.verify(robot).keyRelease(VK_ENTER);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessageKeyPress6() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_6, '6');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).keyPress(54);
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessageKeyPressTurkishLowerSpecialSTypedOnNonTurkishAssistedAsUnicode() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_UNDEFINED, 'ş');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        // 351 as hex 15F - '1', '5', 'F' and finally ENTER are typed in that order
        InOrder inOrder = inOrder(robot);
        inOrder.verify(robot).keyPress(VK_CONTROL);
        inOrder.verify(robot).keyPress(VK_SHIFT);
        inOrder.verify(robot).keyPress(VK_U);
        inOrder.verify(robot).keyRelease(VK_U);
        inOrder.verify(robot).keyRelease(VK_SHIFT);
        inOrder.verify(robot).keyRelease(VK_CONTROL);
        inOrder.verify(robot).keyPress(VK_1);
        inOrder.verify(robot).keyRelease(VK_1);
        inOrder.verify(robot).keyPress(VK_5);
        inOrder.verify(robot).keyRelease(VK_5);
        inOrder.verify(robot).keyPress(VK_F);
        inOrder.verify(robot).keyRelease(VK_F);
        inOrder.verify(robot).keyPress(VK_ENTER);
        inOrder.verify(robot).keyRelease(VK_ENTER);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void testHandleMessageKeyPressTurkishUpperSpecialSTypedOnNonTurkishAssistedAsUnicode() {
        // given
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(PRESSED, VK_UNDEFINED, 'Ş');
        // when
        controlMessageHandler.handleMessage(message);
        // then
        // 350 as hex 15E - '1', '5', 'E' and finally ENTER are typed in that order
        InOrder inOrder = inOrder(robot);
        inOrder.verify(robot).keyPress(VK_CONTROL);
        inOrder.verify(robot).keyPress(VK_SHIFT);
        inOrder.verify(robot).keyPress(VK_U);
        inOrder.verify(robot).keyRelease(VK_U);
        inOrder.verify(robot).keyRelease(VK_SHIFT);
        inOrder.verify(robot).keyRelease(VK_CONTROL);
        inOrder.verify(robot).keyPress(VK_1);
        inOrder.verify(robot).keyRelease(VK_1);
        inOrder.verify(robot).keyPress(VK_5);
        inOrder.verify(robot).keyRelease(VK_5);
        inOrder.verify(robot).keyPress(VK_E);
        inOrder.verify(robot).keyRelease(VK_E);
        inOrder.verify(robot).keyPress(VK_ENTER);
        inOrder.verify(robot).keyRelease(VK_ENTER);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless")
    void testHandleMessageMouseButtonPress1() {
        // given
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(6, 7, NetworkMouseControlMessage.ButtonState.PRESSED, 4);
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).mousePress(InputEvent.BUTTON1_DOWN_MASK);
        verify(robot).mouseMove(6, 7);
    }

    @Test
    @DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless")
    void testHandleMessageMouseButtonPress2() {
        // given
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(4, 40, NetworkMouseControlMessage.ButtonState.PRESSED, 8);
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).mousePress(InputEvent.BUTTON2_DOWN_MASK);
        verify(robot).mouseMove(4, 40);
    }

    @Test
    @DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless")
    void testHandleMessageMouseButtonPress3() {
        // given
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(19, 72, NetworkMouseControlMessage.ButtonState.PRESSED, 16);
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).mousePress(InputEvent.BUTTON3_DOWN_MASK);
        verify(robot).mouseMove(19, 72);
    }

    @Test
    @DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless")
    void testHandleMessageMouseButtonRelease2() {
        // given
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(4, 3, NetworkMouseControlMessage.ButtonState.RELEASED, 8);
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
        verify(robot).mouseMove(4, 3);
    }

    @Test
    @DisabledIf(value = "java.awt.GraphicsEnvironment#isHeadless")
    void testHandleMessageMouseWheeled() {
        // given
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(3, 2, 1);
        // when
        controlMessageHandler.handleMessage(message);
        // then
        verify(robot).mouseWheel(1);
        verify(robot).mouseMove(3, 2);
    }
}