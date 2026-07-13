package com.team02.mopl.global.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

// 스토리지 구현체(Local/S3) 공용 유틸: 업로드 파일 검증 및 파일명/URL 가공
public final class FileStorageUtils {

  // 허용 확장자와 그에 대응하는 정식 image content-type (jpg/jpeg/png/webp, gif·svg 제외)
  private static final Map<String, String> ALLOWED_IMAGE_TYPES =
      Map.of(
          "jpg", "image/jpeg",
          "jpeg", "image/jpeg",
          "png", "image/png",
          "webp", "image/webp");

  private static final Set<String> ALLOWED_EXTENSIONS = ALLOWED_IMAGE_TYPES.keySet();

  // 매직 바이트 판별에 필요한 선두 바이트 수 (WEBP는 12바이트 필요: "RIFF"[0-3] + "WEBP"[8-11])
  private static final int SIGNATURE_LENGTH = 12;

  private FileStorageUtils() {}

  // 업로드 이미지 파일을 검증. 통과 시 저장에 사용할 정식 content-type을 반환
  // (크기 상한은 spring.servlet.multipart.max-file-size가 먼저 게이트한다)
  static String validateImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidFileException("파일이 비어있습니다.");
    }
    String extension = extractExtension(file.getOriginalFilename());
    // 앞의 '.' 제거 후 소문자로 정규화
    String normalized =
        StringUtils.hasText(extension) ? extension.substring(1).toLowerCase(Locale.ROOT) : "";
    if (!ALLOWED_EXTENSIONS.contains(normalized)) {
      throw new InvalidFileException("허용되지 않는 파일 형식입니다.");
    }
    // 확장자로 위장한 파일을 막기 위해 실제 내용(매직 바이트)이 확장자와 일치하는지 검증
    if (!matchesSignature(file, normalized)) {
      throw new InvalidFileException("파일 내용이 확장자와 일치하지 않습니다.");
    }
    return ALLOWED_IMAGE_TYPES.get(normalized);
  }

  // 파일 선두 바이트가 확장자에 대응하는 이미지 시그니처와 일치하는지 확인
  private static boolean matchesSignature(MultipartFile file, String extension) {
    byte[] head = readHead(file);
    return switch (extension) {
        // JPEG: FF D8 FF
      case "jpg", "jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
        // PNG: 89 50 4E 47 0D 0A 1A 0A
      case "png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
        // WEBP: "RIFF"(52 49 46 46) [4바이트 크기] "WEBP"(57 45 42 50)
      case "webp" ->
          startsWith(head, 0x52, 0x49, 0x46, 0x46)
              && head.length >= SIGNATURE_LENGTH
              && head[8] == (byte) 0x57
              && head[9] == (byte) 0x45
              && head[10] == (byte) 0x42
              && head[11] == (byte) 0x50;
      default -> false;
    };
  }

  // 파일 선두 SIGNATURE_LENGTH 바이트를 읽는다 (스트림은 store에서 다시 열어 사용)
  private static byte[] readHead(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return in.readNBytes(SIGNATURE_LENGTH);
    } catch (IOException e) {
      throw new InvalidFileException("파일을 읽을 수 없습니다.");
    }
  }

  // head가 주어진 바이트 시퀀스로 시작하는지 확인
  private static boolean startsWith(byte[] head, int... signature) {
    if (head.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (head[i] != (byte) signature[i]) {
        return false;
      }
    }
    return true;
  }

  // 원본 파일명에서 확장자를 추출 (앞의 '.' 포함, 없으면 빈 문자열)
  static String extractExtension(String originalFilename) {
    if (!StringUtils.hasText(originalFilename)) {
      return "";
    }
    int dot = originalFilename.lastIndexOf('.'); // 마지막 . 의 위치 찾아서
    return dot >= 0 ? originalFilename.substring(dot) : ""; // . 있으면 그 위치부터 끝까지를 확장자로 반환
  }

  // 끝의 슬래시를 제거 (경로/URL 결합 시 슬래시 중복 방지)
  static String stripTrailingSlash(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
