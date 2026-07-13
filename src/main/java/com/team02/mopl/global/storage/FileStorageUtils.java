package com.team02.mopl.global.storage;

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

  private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB

  private FileStorageUtils() {}

  // 업로드 이미지 파일을 검증. 통과 시 저장에 사용할 정식 content-type을 반환
  static String validateImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidFileException("파일이 비어있습니다.");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new InvalidFileException("파일 크기는 최대 10MB까지 허용됩니다.");
    }
    String extension = extractExtension(file.getOriginalFilename());
    // 앞의 '.' 제거 후 소문자로 정규화
    String normalized =
        StringUtils.hasText(extension) ? extension.substring(1).toLowerCase(Locale.ROOT) : "";
    if (!ALLOWED_EXTENSIONS.contains(normalized)) {
      throw new InvalidFileException("허용되지 않는 파일 형식입니다.");
    }
    return ALLOWED_IMAGE_TYPES.get(normalized);
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
