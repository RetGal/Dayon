package mpo.dayon.common.network.message;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetworkClipboardFilesMessageTest {

    @Test
    void testUnmarshall() throws IOException, ClassNotFoundException {
        // given
        ObjectInputStream in = mock(ObjectInputStream.class);
        NetworkClipboardFilesHelper helper = new NetworkClipboardFilesHelper();
        String tmpDir = System.getProperty("java.io.tmpdir");
        FileMetaData metaData = new FileMetaData("test.txt", 100, "basePath");
        List<FileMetaData> metaDataList = new ArrayList<>();
        metaDataList.add(metaData);
        when(in.readObject()).thenReturn(metaDataList);
        when(in.read(any(byte[].class), anyInt(), anyInt())).thenReturn(100);

        // when
        NetworkClipboardFilesHelper result = NetworkClipboardFilesMessage.unmarshall(in, helper, tmpDir);

        // then
        assertNotNull(result);
        assertEquals(1, result.getFileMetadatas().size());
        assertEquals("test.txt", result.getFileMetadatas().get(0).getFileName());
        assertEquals(100, result.getFileMetadatas().get(0).getFileSize());

    }
}
