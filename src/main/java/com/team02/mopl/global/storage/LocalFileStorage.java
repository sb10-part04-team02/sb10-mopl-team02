package com.team02.mopl.global.storage;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

  private final Path basePath; // 파일 실제 저장 루트 경로
  private final String baseUrl; // 저장된 파일에 접근할 때 사용하는 접두사

  // yml으로 부터 설정값 주입 받음
  public LocalFileStorage(
      @Value("${app.storage.local.base-path:storage}") String basePath,
      @Value("${app.storage.local.base-url:/files}") String baseUrl) {
    this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
    this.baseUrl = baseUrl;
  }

  // 업로드된 파일을 로컬 디스크에 저장하고 접근 가능한 URL을 반환
  @Override
  public String store(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "업로드할 파일이 비어 있습니다.");
    }
    // 파일명 충돌 방지 - UUID + 원본확장자 붙이기 (원본 파일명 저장시 - path traversal 등 기타 고려사항 있음)
    String storedName = UUID.randomUUID() + extractExtension(file.getOriginalFilename());
    try {
      Files.createDirectories(basePath); // 저장 루트 디렉터리가 없으면 생성
      Path target = basePath.resolve(storedName).normalize(); // 최종 저장 경로 생성
      file.transferTo(target); // 업로드된 파일의 내용을 실제 저장 경로로 옮김
      log.debug("파일 저장 완료: {}", target);
      return baseUrl + "/" + storedName;
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다.");
    }
  }

  // URL에 해당하는 저장된 파일을 삭제
  @Override
  public void delete(String url) {
    if (!StringUtils.hasText(url) || !url.startsWith(baseUrl + "/")) {
      return;
    }
    // baseUrl 접두사 제거하여 실제 저장 파일명만 추출
    String storedName = url.substring((baseUrl + "/").length());
    try {
      // 해당 파일이 존재하면 삭제 (없으면 예외 없이 false 반환)
      Files.deleteIfExists(basePath.resolve(storedName).normalize());
    } catch (IOException e) {
      log.warn("파일 삭제 실패: {}", url, e); // 삭제 실패는 로그만 남김
    }
  }

  // 원본 파일명에서 확장자를 추출
  private String extractExtension(String originalFilename) {
    if (!StringUtils.hasText(originalFilename)) {
      return "";
    }
    int dot = originalFilename.lastIndexOf('.'); // 마지막 . 의 위치 찾아서
    return dot >= 0 ? originalFilename.substring(dot) : ""; // . 있으면 그 위치부터 끝까지를 확장자로 반환
  }
}
