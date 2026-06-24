package com.team02.mopl.domain.user.mapper;

import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
  UserDto toDto(User user);
}
