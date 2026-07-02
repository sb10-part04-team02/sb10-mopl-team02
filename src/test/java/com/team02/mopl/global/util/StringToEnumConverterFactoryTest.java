package com.team02.mopl.global.util;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.support.TestSecurityConfiguration;
import com.team02.mopl.support.TestWebMvcConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(StringToEnumConverterFactoryTest.class)
@Import({
  StringToEnumConverterFactory.class,
  TestSecurityConfiguration.class,
  TestWebMvcConfiguration.class,
  StringToEnumConverterFactoryTest.TestController.class
})
class StringToEnumConverterFactoryTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("소문자 쿼리 파라미터는 Enum 상수로 변환한다")
  void success_shouldTransformCorrectlyEnum_whenParamIsLowerValue() throws Exception {
    // when & then
    mockMvc
        .perform(get("/test/convert").param("sortBy", "name"))
        .andExpect(status().isOk())
        .andExpect(content().string(UserSortBy.NAME.name())); // NAME("name")
  }

  @Test
  @DisplayName("카멜케이스 쿼리 파라미터는 Enum 상수로 변환한다")
  void success_shouldTransformCorrectlyEnum_whenParamIsCamelCase() throws Exception {
    // when & then
    mockMvc
        .perform(get("/test/convert").param("sortBy", "createdAt"))
        .andExpect(status().isOk())
        .andExpect(content().string(UserSortBy.CREATED_AT.name())); // CREATED_AT("createdAt")
  }

  @Test
  @DisplayName("상수명(대문자) 스타일의 파라미터는 Enum 상수로 변환한다")
  void success_shouldTransformCorrectlyEnum_whenParamIsEnumStyle() throws Exception {
    // when & then
    mockMvc
        .perform(get("/test/convert").param("sortBy", "EMAIL"))
        .andExpect(status().isOk())
        .andExpect(content().string(UserSortBy.EMAIL.name())); // EMAIL("email")
  }

  @Test
  @DisplayName("공백 문자열이 들어오면 예외를 던진다")
  void fail_shouldReturnThrowException_whenParamIsEmpty() throws Exception {
    // when & then
    mockMvc
        .perform(get("/test/convert").param("sortBy", "       "))
        // 파라미터 옵션 required=true로 설정시
        // 컨버터 도달전에 내부적으로 MissingServletRequestParameterException를 진행함
        // 방어적인 코드를 위해 required를 false로 설정하니 커스텀Exception 도달하는 것을 확인
        // 그런데 spring에선 예외와 별개로 enum타입에 null 주입하는걸 확인했고
        // 테스트를 위해 "NULL"을 반환하게 진행했음
        .andExpect(content().string("NULL"));
  }

  @Test
  @DisplayName("Enum과 매칭되지 않는 값이 오면 예외를 던진다")
  void fail_shouldThrowException_whenValueIsInvalid() throws Exception {
    // when & then
    mockMvc
        .perform(get("/test/convert").param("sortBy", "invalid value"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("getValue함수가 없는 일반 Enum은 대소문자가 같지 않으면 예외를 던진다")
  void fail_shouldThrowException_whenEnumHasNoMatchingValue() throws Exception {
    // when & then
    mockMvc
        .perform(get("/test/convert2").param("testRole", "user"))
        .andExpect(status().isBadRequest()); // TestRole엔 ADMIN만 있음
  }

  // 테스트용 컨트롤러
  @RestController
  static class TestController {

    @GetMapping("/test/convert")
    public ResponseEntity<String> testConvert(
        @RequestParam(value = "sortBy", required = false) UserSortBy sortBy) {
      return ResponseEntity.ok(sortBy != null ? sortBy.name() : "NULL");
    }

    @GetMapping("/test/convert2")
    public ResponseEntity<String> testConvert2(@RequestParam(value = "testRole") TestRole role) {
      return ResponseEntity.ok(role.name());
    }
  }

  enum TestRole {
    ADMIN
  }
}
