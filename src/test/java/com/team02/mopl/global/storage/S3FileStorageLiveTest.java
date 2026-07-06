package com.team02.mopl.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

// 실제 AWS S3 버킷을 대상으로 하는 라이브 테스트.
// AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY/AWS_S3_BUCKET/AWS_S3_REGION/AWS_S3_BASE_URL이 모두
// 설정된 환경에서만 동작하며, 기본 test 태스크에서는 제외되고 liveS3Test 태스크로만 수동 실행된다.
@Tag("s3-live")
@DisplayName("S3FileStorage 라이브 테스트 (실제 AWS S3)")
class S3FileStorageLiveTest {

  static String bucket;
  static String baseUrl;
  static S3Client s3;
  static S3FileStorage storage;

  @BeforeAll
  static void setUp() {
    String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
    bucket = System.getenv("AWS_S3_BUCKET");
    String region = System.getenv("AWS_S3_REGION");
    baseUrl = System.getenv("AWS_S3_BASE_URL");

    Assumptions.assumeTrue(
        StringUtils.hasText(accessKey)
            && StringUtils.hasText(bucket)
            && StringUtils.hasText(region)
            && StringUtils.hasText(baseUrl),
        "실제 AWS 자격증명/버킷 정보가 없어 라이브 S3 테스트를 건너뜁니다");

    s3 =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build();
    storage = new S3FileStorage(s3, bucket, baseUrl);
  }

  @AfterAll
  static void tearDown() {
    if (s3 != null) {
      s3.close();
    }
  }

  @Test
  @DisplayName("실제 S3 버킷에 파일을 업로드한다")
  void success_uploadsRealObject() {
    MockMultipartFile file =
        new MockMultipartFile("file", "live-test.png", "image/png", "hello".getBytes());

    String url = storage.store(file);
    String key = url.substring(url.lastIndexOf('/') + 1);

    try {
      assertThat(url).startsWith(baseUrl);
      assertThat(s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()))
          .isNotNull();
    } finally {
      // 업로드 검증용 오브젝트는 테스트 종료 시 정리
      storage.delete(url);
    }
  }

  @Test
  @DisplayName("실제 S3 버킷에서 파일을 삭제한다")
  void success_deletesRealObject() {
    MockMultipartFile file =
        new MockMultipartFile("file", "live-test.png", "image/png", "hello".getBytes());

    // 삭제 대상 오브젝트를 먼저 업로드
    String url = storage.store(file);
    String key = url.substring(url.lastIndexOf('/') + 1);

    storage.delete(url);

    assertThatThrownBy(
            () -> s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()))
        .isInstanceOf(NoSuchKeyException.class);
  }
}
