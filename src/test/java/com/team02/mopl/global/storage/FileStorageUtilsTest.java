package com.team02.mopl.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

  @Nested
  @DisplayName("validateImage")
  class ValidateImage {

    @ParameterizedTest
    @CsvSource({
      "photo.jpg, image/jpeg",
      "photo.jpeg, image/jpeg",
      "photo.PNG, image/png",
      "photo.webp, image/webp"
    })
    @DisplayName("허용 확장자는 확장자 기준의 정식 content-type을 반환한다")
    void success_returnsCanonicalContentType(String filename, String expectedContentType) {
      MockMultipartFile file =
          new MockMultipartFile("file", filename, "application/octet-stream", "data".getBytes());

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
          new MockMultipartFile("file", filename, "image/png", "data".getBytes());

      assertThatThrownBy(() -> FileStorageUtils.validateImage(file))
          .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("10MB를 초과하면 InvalidFileException을 던진다")
    void fail_whenTooLarge() {
      byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
      MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooBig);

      assertThatThrownBy(() -> FileStorageUtils.validateImage(file))
          .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("정확히 10MB는 허용한다")
    void success_whenExactlyMaxSize() {
      byte[] exact = new byte[10 * 1024 * 1024];
      MockMultipartFile file = new MockMultipartFile("file", "max.png", "image/png", exact);

      assertThatCode(() -> FileStorageUtils.validateImage(file)).doesNotThrowAnyException();
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
