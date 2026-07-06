package com.team02.mopl.global.storage;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

  // key가 UUID라 오브젝트 내용이 불변이므로 CDN/브라우저가 장기 캐싱하도록 지정
  // 누구나 캐시 가능 / 1년 / 재검증 없이 캐시 사용
  private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

  private final S3Client s3; // AWS S3 클라이언트
  private final String bucket; // 업로드 대상 버킷 이름
  private final String baseUrl; // 반환/삭제 URL의 공개 접근 접두사 (뒤 슬래시 제거)

  public S3FileStorage(
      S3Client s3,
      @Value("${app.storage.s3.bucket}") String bucket,
      @Value("${app.storage.s3.base-url}") String baseUrl) {
    this.s3 = s3;
    this.bucket = bucket;
    // 뒤 슬래시는 제거해 key와 결합 시 // 중복을 방지
    this.baseUrl = stripTrailingSlash(baseUrl);
  }

  // 업로드된 파일을 S3에 저장하고 접근 가능한 URL을 반환
  @Override
  public String store(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    // 파일명 충돌 방지 - UUID + 원본확장자를 오브젝트 key로 사용
    String key = UUID.randomUUID() + extractExtension(file.getOriginalFilename());
    try {
      PutObjectRequest request =
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(file.getContentType())
              .cacheControl(CACHE_CONTROL)
              .build();
      s3.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
      log.debug("S3 파일 저장 완료: {}/{}", bucket, key);
      return baseUrl + "/" + key;
    } catch (IOException | S3Exception e) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  // URL에 해당하는 S3 오브젝트를 삭제
  @Override
  public void delete(String url) {
    if (!StringUtils.hasText(url)) {
      return;
    }
    // 이 저장소가 관리하는 대상이 아니면 무시
    if (!url.startsWith(baseUrl + "/")) {
      return;
    }
    // baseUrl 접두사 제거하여 실제 오브젝트 key만 추출
    String key = url.substring((baseUrl + "/").length());
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (S3Exception e) {
      log.warn("S3 파일 삭제 실패: {}", url, e); // 삭제 실패는 로그만 남김
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

  // baseUrl 끝의 슬래시를 제거 (key와 결합 시 슬래시 중복 방지)
  private String stripTrailingSlash(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
