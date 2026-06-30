package com.team02.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentRatingService 단위 테스트")
class ContentRatingServiceTest {

  @Mock ContentRepository contentRepository;

  @InjectMocks ContentRatingService contentRatingService;

  private final UUID contentId = UUID.randomUUID();

  @Test
  @DisplayName("재집계가 성공(갱신 행 1건)하면 예외 없이 종료한다")
  void success_whenContentUpdated() {
    // given
    given(contentRepository.refreshRatingAggregate(eq(contentId))).willReturn(1);

    // when & then
    assertThatCode(() -> contentRatingService.refreshAggregate(contentId))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("갱신된 콘텐츠가 없으면(갱신 행 0건) CONTENT_NOT_FOUND BusinessException을 던진다")
  void fail_whenContentNotFound() {
    // given
    given(contentRepository.refreshRatingAggregate(eq(contentId))).willReturn(0);

    // when & then
    assertThatThrownBy(() -> contentRatingService.refreshAggregate(contentId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);
  }
}
