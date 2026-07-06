package com.team02.mopl.domain.user.repository;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public interface UserRepositoryCustom {

  List<User> findUsersByCursor(
      String emailLike,
      Role roleEqual,
      Boolean isLocked,
      Comparable<?> cursor,
      UUID idAfter,
      Integer limit,
      SortDirection sortDirection,
      UserSortBy sortBy);
}
