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
    log.debug("유저 생성 시작: name={}, email={}", request.name(), request.email());
    if (userRepository.existsByEmail(request.email())) {
      throw new UserEmailDuplicateException();
    }

    String encryptedPassword = passwordEncoder.encode(request.password());
    User savedUser =
        userRepository.save(
            new User(request.name(), request.email(), encryptedPassword, null, Role.USER, false));
    UserDto userDto = userMapper.toDto(savedUser);

    log.info("유저 생성 성공: name={}, email={}", userDto.name(), userDto.email());
    return userDto;
  }
}
