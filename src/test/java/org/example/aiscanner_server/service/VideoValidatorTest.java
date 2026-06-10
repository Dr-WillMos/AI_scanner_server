package org.example.aiscanner_server.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class VideoValidatorTest {

    private final VideoValidator validator = new VideoValidator();

    @Test
    @DisplayName("空文件 → 抛异常")
    void emptyFileThrows() {
        var file = new MockMultipartFile("video", "test.mp4", "video/mp4", new byte[0]);
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
        assertEquals("视频文件不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("非 MP4 MIME 类型 → 拋异常")
    void wrongMimeTypeThrows() {
        var file = new MockMultipartFile("video", "test.mp4", "text/plain", new byte[] {1, 2, 3});
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
        assertEquals("仅支持 MP4 视频格式", ex.getMessage());
    }

    @Test
    @DisplayName("null MIME 类型 → 拋异常")
    void nullMimeTypeThrows() {
        var file = new MockMultipartFile("video", "test.mp4", null, new byte[] {1, 2, 3});
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
        assertEquals("仅支持 MP4 视频格式", ex.getMessage());
    }

    @Test
    @DisplayName("非 .mp4 扩展名 → 拋异常")
    void wrongExtensionThrows() {
        var file = new MockMultipartFile("video", "video.avi", "video/mp4", makeMp4Header());
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
        assertEquals("仅支持 .mp4 后缀的视频文件", ex.getMessage());
    }

    @Test
    @DisplayName("null 文件名 → 拋异常")
    void nullFilenameThrows() {
        var file = new MockMultipartFile("video", null, "video/mp4", makeMp4Header());
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
        assertEquals("仅支持 .mp4 后缀的视频文件", ex.getMessage());
    }

    @Test
    @DisplayName("非 MP4 魔数 → 拋异常")
    void wrongMagicBytesThrows() {
        var file = new MockMultipartFile("video", "test.mp4", "video/mp4", new byte[] {0, 0, 0, 0, 'b', 'a', 'd', '!', 0, 0, 0, 0});
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validate(file));
        assertEquals("视频文件已损坏或格式不正确", ex.getMessage());
    }

    @Test
    @DisplayName("正常 MP4 文件 → 通过")
    void validMp4Passes() {
        var file = new MockMultipartFile("video", "video.mp4", "video/mp4", makeMp4Header());
        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    @DisplayName("大写扩展名 .MP4 → 通过")
    void uppercaseExtensionPasses() {
        var file = new MockMultipartFile("video", "VIDEO.MP4", "video/mp4", makeMp4Header());
        assertDoesNotThrow(() -> validator.validate(file));
    }

    /** Build a minimal valid MP4 header: 12 bytes with "ftyp" at offset 4. */
    private byte[] makeMp4Header() {
        byte[] header = new byte[12];
        // box size = 32 bytes (0x00000020)
        header[0] = 0;
        header[1] = 0;
        header[2] = 0;
        header[3] = 32;
        // "ftyp"
        header[4] = 'f';
        header[5] = 't';
        header[6] = 'y';
        header[7] = 'p';
        // minor version + compatible brand
        header[8] = 0;
        header[9] = 0;
        header[10] = 0;
        header[11] = 0;
        return header;
    }
}
