package com.team02.mopl.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.global.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

// Testcontainers를 사용해 실제 S3 대신 LocalStack(로컬 AWS 애뮬레이터) 컨테이너를 띄워 테스트
// 즉 실제 AWS 자격증명/네트워크 없이도 S3 동작을 검증할 수 있는 통합 테스트 구성
@Testcontainers
@DisplayName("S3FileStorage 테스트 (LocalStack)")
class S3FileStorageTest {

  private static final String BUCKET = "test-bucket";
  private static final String BASE_URL = "https://cdn.example.com/uploads";

  // @Container: Testcontainers가 테스트 클래스 생명주기에 맞춰 자동으로 시작/종료
  // localstack:3.4 이미지로 S3 서비스만 활성화한 컨테이너를 실행
  @Container
  static final LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
          .withServices(LocalStackContainer.Service.S3);

  static S3Client s3;
  static S3FileStorage storage;

  @BeforeAll
  static void setUp() {
    s3 =
        S3Client.builder()
            // LocalStack 컨테이너의 엔드포인트로 요청을 보내도록 오버라이드
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            // LocalStack이 발급한 테스트용 더미 액세스키/시크릿키를 고정값으로 사용
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .forcePathStyle(true) // LocalStack은 path-style 접근이 필요
            .build();
    // 테스트에 사용할 버킷을 미리 생성
    s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    storage = new S3FileStorage(s3, BUCKET, BASE_URL);
  }

  @AfterAll
  static void tearDown() {
    if (s3 != null) {
      s3.close();
    }
  }

  // key가 S3에 실제로 존재하는지 확인
  private boolean objectExists(String key) {
    try {
      s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  // 반환 URL에서 baseUrl 접두사를 벗겨 key만 추출
  private String keyOf(String url) {
    return url.substring((BASE_URL + "/").length());
  }

  @Nested
  @DisplayName("store")
  class Store {

    @Test
    @DisplayName("파일을 S3에 업로드하고 baseUrl 접두사가 붙은 URL을 반환한다")
    void success_uploadsAndReturnsUrl() {
      MockMultipartFile file =
          new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());

      String url = storage.store(file);

      assertThat(url).startsWith(BASE_URL + "/").endsWith(".png");
      assertThat(objectExists(keyOf(url))).isTrue();
    }

    @Test
    @DisplayName("업로드된 오브젝트에 장기 캐싱 Cache-Control 메타데이터가 설정된다")
    void success_setsCacheControlMetadata() {
      MockMultipartFile file =
          new MockMultipartFile("file", "cached.png", "image/png", "hello".getBytes());

      String url = storage.store(file);

      var head = s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(keyOf(url)).build());
      assertThat(head.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    @DisplayName("파일이 null이면 BusinessException을 던진다")
    void fail_whenFileIsNull() {
      assertThatThrownBy(() -> storage.store(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("파일이 비어있으면 BusinessException을 던진다")
    void fail_whenFileIsEmpty() {
      MockMultipartFile empty =
          new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

      assertThatThrownBy(() -> storage.store(empty)).isInstanceOf(BusinessException.class);
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("저장된 파일의 URL로 삭제하면 S3에서 오브젝트가 사라진다")
    void success_deletesObject() {
      MockMultipartFile file =
          new MockMultipartFile("file", "doc.txt", "text/plain", "data".getBytes());
      String url = storage.store(file);
      String key = keyOf(url);
      assertThat(objectExists(key)).isTrue();

      storage.delete(url);

      assertThat(objectExists(key)).isFalse();
    }

    @Test
    @DisplayName("이 저장소가 관리하지 않는 URL이나 빈 값은 예외 없이 무시한다")
    void ignore_whenUnmanagedOrBlank() {
      assertThatCode(
              () -> {
                storage.delete(null);
                storage.delete("");
                storage.delete("https://other.example.com/foo/bar.png");
              })
          .doesNotThrowAnyException();
    }
  }
}
