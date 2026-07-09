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
  private final String baseUrl; // 저장된 파일을 서빙하는 URL 경로 접두사 (예: /files)
  private final String
      publicBaseUrl; // 반환 URL 앞에 붙는 origin 접두사 (dev: http://localhost:8080, prod: 빈 값)

  // yml으로 부터 설정값 주입 받음
  public LocalFileStorage(
      @Value("${app.storage.local.base-path:storage}") String basePath,
      @Value("${app.storage.local.base-url:/files}") String baseUrl,
      @Value("${app.storage.local.public-base-url:}") String publicBaseUrl) {
    this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
    this.baseUrl = baseUrl;
    // 뒤 슬래시는 제거해 baseUrl과 결합 시 // 중복을 방지
    this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
  }

  // 업로드된 파일을 로컬 디스크에 저장하고 접근 가능한 URL을 반환
  @Override
  public String store(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    // 파일명 충돌 방지 - UUID + 원본확장자 붙이기 (원본 파일명 저장시 - path traversal 등 기타 고려사항 있음)
    String storedName = UUID.randomUUID() + extractExtension(file.getOriginalFilename());
    try {
      Files.createDirectories(basePath); // 저장 루트 디렉터리가 없으면 생성
      Path target = basePath.resolve(storedName).normalize(); // 최종 저장 경로 생성
      if (!target.startsWith(basePath)) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
      }
      file.transferTo(target); // 업로드된 파일의 내용을 실제 저장 경로로 옮김
      log.debug("파일 저장 완료: {}", target);
      // publicBaseUrl이 있으면 절대 URL(dev), 없으면 상대 경로(prod, 같은 origin)
      return publicBaseUrl + baseUrl + "/" + storedName;
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  // URL에 해당하는 저장된 파일을 삭제
  @Override
  public void delete(String url) {
    if (!StringUtils.hasText(url)) {
      return;
    }
    // 절대 URL(dev)이면 origin 접두사를 먼저 벗겨 서빙 경로만 남김
    String path = url;
    if (StringUtils.hasText(publicBaseUrl) && path.startsWith(publicBaseUrl)) {
      path = path.substring(publicBaseUrl.length());
    }
    // 이 저장소가 관리하는 대상이 아니면 무시
    if (!path.startsWith(baseUrl + "/")) {
      return;
    }
    // baseUrl 접두사 제거하여 실제 저장 파일명만 추출
    String storedName = path.substring((baseUrl + "/").length());
    try {
      Path resolved = basePath.resolve(storedName).normalize();
      // basePath 밖을 가리키는 경로 탈출 시도 차단
      if (!resolved.startsWith(basePath)) {
        log.warn("경로 탈출 시도 감지, 삭제 건너뜀: {}", url);
        return;
      }
      // 해당 파일이 존재하면 삭제 (없으면 예외 없이 false 반환)
      Files.deleteIfExists(resolved);
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

  // publicBaseUrl 끝의 슬래시를 제거 (baseUrl과 결합 시 슬래시 중복 방지)
  private String stripTrailingSlash(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
