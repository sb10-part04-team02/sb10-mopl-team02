package com.team02.mopl.domain.user.service;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public UserDto createUser(UserCreateRequest request) {
    log.debug("유저 생성 시작: email={}", maskValidEmail(request.email()));

    if (userRepository.existsByEmail(request.email())) {
      throw new UserEmailDuplicateException();
    }

    String encryptedPassword = passwordEncoder.encode(request.password());
    // 광클이나 서비스, 네트워크 상황 대응으로의 2차 체크
    User savedUser;
    try {
      savedUser =
          userRepository.save(
              new User(request.name(), request.email(), encryptedPassword, null, Role.USER, false));
    } catch (DataIntegrityViolationException e) {
      throw new UserEmailDuplicateException();
    }
    UserDto userDto = userMapper.toDto(savedUser);

    log.info("유저 생성 성공: userId={}, email={}", userDto.id(), maskValidEmail(userDto.email()));
    return userDto;
  }

  private String maskValidEmail(String validEmail) {
    String[] parts = validEmail.split("@");
    String local = parts[0];
    String domain = parts[1];

    if (local.length() <= 2) {
      return "**@" + domain;
    }

    // 앞글자 2글자만 공개
    return local.substring(0, 2) + "*".repeat(local.length() - 2) + "@" + domain;
  }
}
