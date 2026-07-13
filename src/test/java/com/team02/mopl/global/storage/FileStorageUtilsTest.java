package com.team02.mopl.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("FileStorageUtils 테스트")
class FileStorageUtilsTest {

  // 확장자에 대응하는 유효한 이미지 매직 바이트를 앞에 붙인 페이로드 생성
  private static byte[] imageBytes(String extension) {
    return switch (extension) {
      case "jpg", "jpeg" -> new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
      case "png" -> new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
      case "webp" -> new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};
      default -> throw new IllegalArgumentException(extension);
    };
  }

  @Nested
  @DisplayName("validateImage")
  class ValidateImage {

    @ParameterizedTest
    @CsvSource({
      "photo.jpg, jpg, image/jpeg",
      "photo.jpeg, jpeg, image/jpeg",
      "photo.PNG, png, image/png",
      "photo.webp, webp, image/webp"
    })
    @DisplayName("허용 확장자이고 매직 바이트가 일치하면 정식 content-type을 반환한다")
    void success_returnsCanonicalContentType(
        String filename, String extension, String expectedContentType) {
      MockMultipartFile file =
          new MockMultipartFile(
              "file", filename, "application/octet-stream", imageBytes(extension));

      assertThat(FileStorageUtils.validateImage(file)).isEqualTo(expectedContentType);
    }

    @Test
    @DisplayName("파일이 null이면 InvalidFileException을 던진다")
    void fail_whenNull() {
      assertThatThrownBy(() -> FileStorageUtils.validateImage(null))
          .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("파일이 비어있으면 InvalidFileException을 던진다")
    void fail_whenEmpty() {
      MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

      assertThatThrownBy(() -> FileStorageUtils.validateImage(empty))
          .isInstanceOf(InvalidFileException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"note.txt", "malware.exe", "vector.svg", "anim.gif", "photo"})
    @DisplayName("허용되지 않는 확장자나 확장자 없음은 InvalidFileException을 던진다")
    void fail_whenExtensionNotAllowed(String filename) {
      MockMultipartFile file =
          new MockMultipartFile("file", filename, "image/png", imageBytes("png"));

      assertThatThrownBy(() -> FileStorageUtils.validateImage(file))
          .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("확장자는 허용되지만 매직 바이트가 확장자와 불일치하면 InvalidFileException을 던진다")
    void fail_whenSignatureMismatch() {
      // .png 확장자지만 내용은 JPEG 시그니처 (위장 파일)
      MockMultipartFile spoofed =
          new MockMultipartFile("file", "spoofed.png", "image/png", imageBytes("jpg"));

      assertThatThrownBy(() -> FileStorageUtils.validateImage(spoofed))
          .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("이미지 시그니처가 없는 임의 바이트는 InvalidFileException을 던진다")
    void fail_whenNoSignature() {
      MockMultipartFile fake =
          new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());

      assertThatThrownBy(() -> FileStorageUtils.validateImage(fake))
          .isInstanceOf(InvalidFileException.class);
    }
  }

  @Nested
  @DisplayName("extractExtension")
  class ExtractExtension {

    @ParameterizedTest
    @CsvSource({"photo.png, .png", "a.b.jpg, .jpg", "noext, ''", "'', ''"})
    @DisplayName("마지막 점 이후를 확장자로 추출하고, 없으면 빈 문자열을 반환한다")
    void extractsExtension(String input, String expected) {
      assertThat(FileStorageUtils.extractExtension(input)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("stripTrailingSlash")
  class StripTrailingSlash {

    @ParameterizedTest
    @CsvSource({"https://cdn/x/, https://cdn/x", "https://cdn/x, https://cdn/x", "'', ''"})
    @DisplayName("끝의 슬래시를 제거하고, 없으면 그대로 반환한다")
    void stripsTrailingSlash(String input, String expected) {
      assertThat(FileStorageUtils.stripTrailingSlash(input)).isEqualTo(expected);
    }
  }
}
