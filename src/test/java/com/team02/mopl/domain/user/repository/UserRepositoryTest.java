package com.team02.mopl.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.tuple;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.domain.user.util.UserCursorConverter;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryTest extends RepositoryTestSupport {

  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  @Test
  @DisplayName("필수 파라미터만으로 조회할경우 유저 전체를 조회한다")
  void success_shouldRetrieveAllUsers_whenOnlyRequiredParametersAreProvided() {
    // given
    saveUser("user1", "user1@gmail.com", Role.USER, false);
    saveUser("user2", "user2@naver.com", Role.USER, true);
    saveUser("user3", "user3@gmail.com", Role.ADMIN, false);

    // when
    List<User> result =
        userRepository.findUsersByCursor(
            null, null, null, null, null, 5, SortDirection.ASCENDING, UserSortBy.NAME);

    // then
    assertThat(result).hasSize(3);
  }

  @Test
  @DisplayName("limit가 전체 user 수보다 적으면 limit 만큼만 유저를 반환한다")
  void success_shouldReturnExactlyLimitSize_whenLimitIsLessThanTotalUserCount() {
    // given
    saveUser("user1", "user1@gmail.com", Role.USER, false);
    saveUser("user2", "user2@naver.com", Role.USER, true);
    saveUser("user3", "user3@gmail.com", Role.ADMIN, false);
    int limit = 2;

    // when
    List<User> result =
        userRepository.findUsersByCursor(
            null, null, false, null, null, limit, SortDirection.ASCENDING, UserSortBy.NAME);

    // then
    assertThat(result.size()).isEqualTo(limit);
  }

  @Test
  @DisplayName("검색옵션이 제공되면 해당하는 유저를 찾아 반환한다")
  void success_shouldReturnMatchingUsers_whenSearchOptionsAreProvided() {
    // given
    saveUser("user1", "user1@example.com", Role.USER, false);
    saveUser("user2", "user2@gmail.com", Role.ADMIN, false);
    saveUser("user3", "user3@example.com", Role.ADMIN, true);
    String emailLike = "example";
    Role role = Role.ADMIN;
    boolean isLocked = true;

    // when
    List<User> result =
        userRepository.findUsersByCursor(
            emailLike, role, isLocked, null, null, 10, SortDirection.ASCENDING, UserSortBy.NAME);

    // then
    assertThat(result)
        .hasSize(1)
        .first()
        .satisfies(
            user -> {
              assertThat(user.getName()).isEqualTo("user3");
              assertThat(user.getEmail()).contains(emailLike);
              assertThat(user.getRole()).isEqualTo(role);
              assertThat(user.isLocked()).isEqualTo(isLocked);
            });
  }

  @Test
  @DisplayName("이름을 커서로 페이징할때 정렬 순서에 맞춰 유저를 반환한다")
  void success_shouldReturnUsersInOrder_whenPagingByNameCursor() {
    // given
    SortDirection direction = SortDirection.ASCENDING;
    UserSortBy sortBy = UserSortBy.NAME;

    saveUser("A_user", "A0@gmail.com", Role.USER, false);
    saveUser("B_user", "B1@gmail.com", Role.USER, false);
    saveUser("B_user", "B2@gmail.com", Role.USER, false);
    saveUser("C_user", "C@gmail.com", Role.USER, false);
    List<User> totalUser =
        userRepository.findUsersByCursor(null, null, null, null, null, 10, direction, sortBy);

    User cursorUser = totalUser.get(1);
    User user1 = totalUser.get(2);
    User user2 = totalUser.get(3);
    Comparable<?> cursor = UserCursorConverter.toSortKey(sortBy, cursorUser.getName());

    // when
    List<User> result =
        userRepository.findUsersByCursor(
            null, null, null, cursor, cursorUser.getId(), 10, direction, sortBy);

    // then
    assertThat(cursorUser.getName()).isEqualTo(user1.getName());
    assertThat(result)
        .hasSize(2)
        .extracting(User::getName, User::getId)
        .containsExactly( // user1 < user2 순서 확인 포함
            tuple(user1.getName(), user1.getId()), tuple(user2.getName(), user2.getId()));
  }

  @Test
  @DisplayName("이메일을 커서로 페이징할때 정렬 순서에 맞춰 유저를 반환한다")
  void success_shouldReturnUsersInOrder_whenPagingByEmailCursor() {
    // given
    SortDirection direction = SortDirection.ASCENDING;
    UserSortBy sortBy = UserSortBy.EMAIL; // email은 unique 속성

    saveUser("A_user", "A@gmail.com", Role.USER, false);
    saveUser("B_user", "B@gmail.com", Role.USER, false);
    saveUser("C_user", "C@gmail.com", Role.USER, false);
    saveUser("D_user", "D@gmail.com", Role.USER, false);
    List<User> totalUser =
        userRepository.findUsersByCursor(null, null, null, null, null, 10, direction, sortBy);

    User cursorUser = totalUser.get(1);
    User user1 = totalUser.get(2);
    User user2 = totalUser.get(3);
    Comparable<?> cursor = UserCursorConverter.toSortKey(sortBy, cursorUser.getEmail());

    // when
    List<User> result =
        userRepository.findUsersByCursor(
            null, null, null, cursor, cursorUser.getId(), 10, direction, sortBy);

    // then
    assertThat(cursorUser.getEmail()).isNotEqualTo(user1.getEmail());
    assertThat(result)
        .hasSize(2)
        .extracting(User::getEmail, User::getId)
        .containsExactly( // user1 < user2 순서 확인 포함
            tuple(user1.getEmail(), user1.getId()), tuple(user2.getEmail(), user2.getId()));
  }

  @Test
  @DisplayName("생성시간을 커서로 페이징할때 정렬 순서에 맞춰 유저를 반환한다")
  void success_shouldReturnUsersInOrder_whenPagingByCreatedAtCursor() {
    // given
    SortDirection direction = SortDirection.DESCENDING; // 테스트 거버리지용 역
    UserSortBy sortBy = UserSortBy.CREATED_AT;

    saveUser("A_user", "A@gmail.com", Role.USER, false);
    saveUser("B_user", "B@gmail.com", Role.USER, false);
    saveUser("C_user", "C@gmail.com", Role.USER, false);
    saveUser("D_user", "D@gmail.com", Role.USER, false);
    List<User> totalUser =
        userRepository.findUsersByCursor(null, null, null, null, null, 10, direction, sortBy);

    List<User> sortedUser =
        totalUser.stream()
            .sorted(
                Comparator
                    // 1차 정렬기준(createdAt)
                    .comparing(User::getCreatedAt)
                    .reversed()
                    // 2차 정렬기준(id. DB와 같은 Unsigned 방식. 내림차순 정렬)
                    .thenComparing(User::getId, DB_UUID_COMPARATOR.reversed()))
            .toList();

    User cursorUser = sortedUser.get(1);
    User user1 = sortedUser.get(2);
    User user2 = sortedUser.get(3);
    Comparable<?> cursor =
        UserCursorConverter.toSortKey(sortBy, cursorUser.getCreatedAt().toString());

    // when
    List<User> result =
        userRepository.findUsersByCursor(
            null, null, null, cursor, cursorUser.getId(), 10, direction, sortBy);

    // then
    assertThat(result)
        .hasSize(2)
        .extracting(User::getCreatedAt, User::getId)
        .containsExactly( // user1 < user2 순서 확인 포함
            tuple(user1.getCreatedAt(), user1.getId()), tuple(user2.getCreatedAt(), user2.getId()));
  }

  @Test
  @DisplayName("잠금여부를 커서로 페이징할때 정렬 순서에 맞춰 유저를 반환한다")
  void success_shouldReturnUsersInOrder_whenPagingByIsLockedCursor() {
    // given
    SortDirection direction = SortDirection.ASCENDING;
    UserSortBy sortBy = UserSortBy.IS_LOCKED;

    saveUser("A_user", "A@gmail.com", Role.USER, false);
    saveUser("B_user", "B@gmail.com", Role.USER, true);
    saveUser("C_user", "C@gmail.com", Role.USER, false);
    saveUser("D_user", "D@gmail.com", Role.USER, false);
    List<User> totalUser =
        userRepository.findUsersByCursor(null, null, null, null, null, 10, direction, sortBy);

    // 잠금여부 오름차순일땐 false -> true
    List<User> sortedUser =
        totalUser.stream()
            .sorted(
                Comparator
                    // 1차 정렬기준(isLocked)
                    .comparing(User::isLocked)
                    // 2차 정렬기준(id. DB와 같은 Unsigned 방식 오름차순 정렬)
                    .thenComparing(User::getId, DB_UUID_COMPARATOR))
            .toList();

    User cursorUser = sortedUser.get(1);
    User user1 = sortedUser.get(2);
    User user2 = sortedUser.get(3);
    Comparable<?> cursor =
        UserCursorConverter.toSortKey(sortBy, Boolean.toString(cursorUser.isLocked()));

    // when
    List<User> result =
        userRepository.findUsersByCursor(
            null, null, null, cursor, cursorUser.getId(), 10, direction, sortBy);

    // then
    assertThat(result)
        .hasSize(2)
        .extracting(User::isLocked, User::getId)
        .containsExactly( // user1 < user2 순서 확인 포함
            tuple(user1.isLocked(), user1.getId()), tuple(user2.isLocked(), user2.getId()));
  }

  @Test
  @DisplayName("권한을 커서로 페이징할때 정렬 순서에 맞춰 유저를 반환한다")
  void success_shouldReturnUsersInOrder_whenPagingByRoleCursor() {
    // given
    SortDirection direction = SortDirection.ASCENDING;
    UserSortBy sortBy = UserSortBy.ROLE;

    saveUser("A_user", "A@gmail.com", Role.USER, false);
    saveUser("B_user", "B@gmail.com", Role.USER, true);
    saveUser("C_user", "C@gmail.com", Role.ADMIN, false);
    saveUser("D_user", "D@gmail.com", Role.USER, false);
    List<User> totalUser =
        userRepository.findUsersByCursor(null, null, null, null, null, 10, direction, sortBy);

    // 권한 오름차순일땐 USER -> ADMIN
    List<User> sortedUser =
        totalUser.stream()
            .sorted(
                Comparator
                    // 1차 정렬기준(Role)
                    .comparing((User u) -> u.getRole().name())
                    // 2차 정렬기준(id. DB와 같은 Unsigned 방식 오름차순 정렬)
                    .thenComparing(User::getId, DB_UUID_COMPARATOR))
            .toList();

    User cursorUser = sortedUser.get(1);
    User user1 = sortedUser.get(2);
    User user2 = sortedUser.get(3);
    Comparable<?> cursor = UserCursorConverter.toSortKey(sortBy, cursorUser.getRole().name());

    // when
    List<User> result =
        userRepository.findUsersByCursor(
            null, null, null, cursor, cursorUser.getId(), 10, direction, sortBy);

    // then
    assertThat(result)
        .hasSize(2)
        .extracting(User::getRole, User::getId)
        .containsExactly( // user1 < user2 순서 확인 포함
            tuple(user1.getRole(), user1.getId()), tuple(user2.getRole(), user2.getId()));
  }

  @Test
  @DisplayName("검색 파라미터가 모두 null이어도 전체 유저 수를 반환한다")
  void success_shouldCountAllUsers_whenSearchParametersAreNull() {
    // given
    saveUser("user1", "user1@gmail.com", Role.USER, false);
    saveUser("user2", "user2@naver.com", Role.USER, true);
    saveUser("user3", "user3@gmail.com", Role.ADMIN, false);

    // when
    long count = userRepository.countUsersByCursor(null, null, null);

    // then
    assertThat(count).isEqualTo(3);
  }

  @Test
  @DisplayName("검색 파라미터가 제공되면 각 조건이 독립적으로 적용된 유저 수를 반환한다")
  void success_shouldCountMatchingUsers_whenSearchParametersAreProvided() {
    // given
    saveUser("user1", "user1@example.com", Role.USER, false);
    saveUser("user2", "user2@gmail.com", Role.ADMIN, false);
    saveUser("user3", "user3@example.com", Role.ADMIN, true);

    // when & then (각 필터 단독 적용 시 전체 수(3)와 다른 값이어야 조건 누락 회귀를 잡을 수 있다)
    assertThat(userRepository.countUsersByCursor("example", null, null)).isEqualTo(2);
    assertThat(userRepository.countUsersByCursor(null, Role.ADMIN, null)).isEqualTo(2);
    assertThat(userRepository.countUsersByCursor(null, null, true)).isEqualTo(1);
    assertThat(userRepository.countUsersByCursor("example", Role.ADMIN, true)).isEqualTo(1);
  }

  private User saveUser(String name, String email, Role role, boolean isLocked) {
    User user = new User(name, email, "pwd", null, role, isLocked);
    em.persist(user); // 영속화
    return user;
  }

  private static final Comparator<UUID> DB_UUID_COMPARATOR =
      (id1, id2) -> {
        int mostSig =
            Long.compareUnsigned(id1.getMostSignificantBits(), id2.getMostSignificantBits());
        if (mostSig != 0) {
          return mostSig;
        }
        return Long.compareUnsigned(id1.getLeastSignificantBits(), id2.getLeastSignificantBits());
      };
}
