package com.team02.mopl.global.init;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InitAdminTest {

  @Mock private UserService userService;
  @InjectMocks private InitAdmin initAdmin;

  @Test
  @DisplayName("application구동시 어드민 계정이 정상적으로 생성된다")
  void success_shouldCreateAdminAccount_whenApplicationBoots() throws Exception {
    // given
    String adminEmail = "admin@gmail.com";
    String adminPassword = "password";
    String adminName = "어드민이름";
    ReflectionTestUtils.setField(initAdmin, "adminEmail", adminEmail);
    ReflectionTestUtils.setField(initAdmin, "adminPassword", adminPassword);
    ReflectionTestUtils.setField(initAdmin, "adminName", adminName);

    UserDto mockDto = mock(UserDto.class);
    given(userService.createUser(any(UserCreateRequest.class))).willReturn(mockDto);
    ApplicationArguments mockArgs = mock(ApplicationArguments.class);

    // when
    initAdmin.run(mockArgs);

    // then
    then(userService).should(times(1)).createUser(any(UserCreateRequest.class));
  }

  @Test
  @DisplayName("어드민이 이미 존재할 경우 초기화 생성을 무시한다")
  void success_shouldSkipInitialization_whenAdminAlreadyExists() {
    // given
    given(userService.createUser(any(UserCreateRequest.class)))
        .willThrow(UserEmailDuplicateException.class);

    // when & then
    assertDoesNotThrow(() -> initAdmin.run(mock(ApplicationArguments.class)));
  }
}
