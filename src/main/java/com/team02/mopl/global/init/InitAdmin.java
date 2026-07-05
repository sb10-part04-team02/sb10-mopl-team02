package com.team02.mopl.global.init;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitAdmin implements ApplicationRunner {

  private final UserService userService;

  @Value("${app.admin.email}")
  private String adminEmail;

  @Value("${app.admin.password}")
  private String adminPassword;

  @Value("${app.admin.name}")
  private String adminName;

  @Override
  public void run(ApplicationArguments args) throws Exception {

    // TODO: 기본구현 후 분산기능 도입 시 분산기능 추가
    UserCreateRequest request = new UserCreateRequest(adminName, adminEmail, adminPassword);
    UserDto adminDto = userService.createUser(request);

    // TODO: 권한변경 기능 추가되면 추가예정

    log.info("어드민 계정 생성: adminId={}", adminDto.id());
  }
}
