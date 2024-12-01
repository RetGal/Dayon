package mpo.dayon.common.network;

import mpo.dayon.common.capture.Gray8Bits;
import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.buffer.MemByteBuffer;
import mpo.dayon.common.capture.CaptureEngineConfiguration;
import mpo.dayon.common.capture.CaptureTile;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMessageType;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;
import mpo.dayon.common.squeeze.CompressionMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.awt.*;
import java.awt.im.InputContext;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

import static java.lang.Long.sum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NetworkSenderTest {

    private static final byte MAGIC_NUMBER = (byte) 170;
    private static final int PRESSED = 1;
    private static final int RELEASED = 1 << 1;

    private ObjectOutputStream outMock;
    private ArgumentCaptor<Integer> valueCaptor;
    private NetworkSender sender;

    @BeforeEach
    void init() {
        outMock = Mockito.mock(ObjectOutputStream.class);
        valueCaptor = ArgumentCaptor.forClass(int.class);
        sender = new NetworkSender(outMock);
        sender.start(1);
    }

    static boolean isLocaleNull() {
        return InputContext.getInstance().getLocale() == null;
    }

    @Test
    @DisabledIf("isLocaleNull")
    void sendHello() throws IOException {
        // given
        final char osId = 'l';
        final String inputLocale = InputContext.getInstance().getLocale().toString();
        // when
        sender.sendHello(osId);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.HELLO.ordinal());
        verify(outMock, times(2)).writeInt(anyInt());
        verify(outMock).writeChar(osId);
        verify(outMock).writeUTF(inputLocale);
    }


    @Test
    void sendCapture() throws IOException {
        // given
        CaptureTile[] dirty = new CaptureTile[0];
        int noNewCompressionConfig = 0;
        int captureId = 1;
        // when
        sender.sendCapture(captureId, CompressionMethod.NONE, null, new MemByteBuffer());
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.CAPTURE.ordinal());
        verify(outMock, times(2)).writeInt(valueCaptor.capture());
        verify(outMock).writeByte(noNewCompressionConfig);
        final List<Integer> capturedValues = valueCaptor.getAllValues();
        int first = capturedValues.get(0);
        int last = capturedValues.get(capturedValues.size() - 1);
        assertEquals(captureId, first);
        assertEquals(dirty.length, last);
    }

    @Test
    void sendMouseLocation() throws IOException {
        // given
        Point location = new Point(7, 21);
        // when
        sender.sendMouseLocation(location);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.MOUSE_LOCATION.ordinal());
        verify(outMock, times(2)).writeShort(valueCaptor.capture());
        final List<Integer> capturedValues = valueCaptor.getAllValues();
        int first = capturedValues.get(0);
        int last = capturedValues.get(capturedValues.size() - 1);
        assertEquals(location.x, first);
        assertEquals(location.y, last);
    }

    @Test
    void sendCaptureConfigurationToLegacyPeerShouldNotIncludeCaptureColorValue() throws IOException {
        // given
        CaptureEngineConfiguration configuration = new CaptureEngineConfiguration();
        boolean monochromePeer = true;
        // when
        sender.sendCaptureConfiguration(configuration, monochromePeer);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.CAPTURE_CONFIGURATION.ordinal());
        verify(outMock).write(configuration.getCaptureQuantization().ordinal());
        verify(outMock).writeInt(configuration.getCaptureTick());
    }

    @Test
    void sendCaptureConfigurationIncludeTheRightCaptureColorValue() throws IOException {
        // given
        CaptureEngineConfiguration configuration = new CaptureEngineConfiguration(333, Gray8Bits.X_16, true);
        boolean monochromePeer = false;
        // when
        sender.sendCaptureConfiguration(configuration, monochromePeer);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.CAPTURE_CONFIGURATION.ordinal());
        verify(outMock).write(configuration.getCaptureQuantization().ordinal());
        verify(outMock).writeInt(configuration.getCaptureTick());
        verify(outMock).writeShort(1);
    }

    @Test
    void sendCompressorConfiguration() throws IOException {
        // given
        CompressorEngineConfiguration configuration = new CompressorEngineConfiguration();
        // when
        sender.sendCompressorConfiguration(configuration);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.COMPRESSOR_CONFIGURATION.ordinal());
        verify(outMock).write(configuration.getMethod().ordinal());
        verify(outMock).writeInt(configuration.getCacheMaxSize());
        verify(outMock).writeInt(configuration.getCachePurgeSize());
    }

    @Test
    void sendMouseControl() throws IOException {
        // given
        final int x = 1;
        final int y = 3;
        NetworkMouseControlMessage message = new NetworkMouseControlMessage(x, y, NetworkMouseControlMessage.ButtonState.RELEASED, NetworkMouseControlMessage.BUTTON1);
        // when
        sender.sendMouseControl(message);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.MOUSE_CONTROL.ordinal());
        verify(outMock, times(2)).writeShort(valueCaptor.capture());
        verify(outMock, times(1)).writeInt(valueCaptor.capture());
        final List<Integer> capturedValues = valueCaptor.getAllValues();
        int first = capturedValues.get(0);
        int second = capturedValues.get(1);
        int last = capturedValues.get(capturedValues.size() - 1);
        assertEquals(x, first);
        assertEquals(y, second);
        assertEquals(sum(RELEASED, NetworkMouseControlMessage.BUTTON1), last);
    }

    @Test
    void sendKeyControl() throws IOException {
        // given
        final int keyCode = 61;
        final char keyChar = 'a';
        NetworkKeyControlMessage message = new NetworkKeyControlMessage(NetworkKeyControlMessage.KeyState.PRESSED, keyCode, keyChar);
        // when
        sender.sendKeyControl(message);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.KEY_CONTROL.ordinal());
        verify(outMock, times(2)).writeInt(valueCaptor.capture());
        verify(outMock, times(1)).writeChar(valueCaptor.capture());
        final List<Integer> capturedValues = valueCaptor.getAllValues();
        int first = capturedValues.get(0);
        int second = capturedValues.get(1);
        int last = capturedValues.get(capturedValues.size() - 1);
        assertEquals(PRESSED, first);
        assertEquals(keyCode, second);
        assertEquals(keyChar, last);
    }

    @Test
    void sendRemoteClipboardRequest() throws IOException {
        // when
        sender.sendRemoteClipboardRequest();
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.CLIPBOARD_REQUEST.ordinal());
    }

    @Test
    void sendClipboardContentText() throws IOException {
        // given
        final String payload = "hakuma matata";
        // when
        sender.sendClipboardContentText(payload, payload.getBytes().length);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.CLIPBOARD_TEXT.ordinal());
        verify(outMock).writeUTF(payload);
    }

    @Test
    void sendResizeScreen() throws IOException {
        // given
        final int width = 256;
        final int height = 32;
        // when
        sender.sendResizeScreen(width, height);
        // then
        verify(outMock, timeout(50)).writeByte(MAGIC_NUMBER);
        verify(outMock).write(NetworkMessageType.RESIZE.ordinal());
        verify(outMock, times(2)).writeInt(valueCaptor.capture());
        final List<Integer> capturedValues = valueCaptor.getAllValues();
        int first = capturedValues.get(0);
        int last = capturedValues.get(capturedValues.size() - 1);
        assertEquals(width, first);
        assertEquals(height, last);
    }
}