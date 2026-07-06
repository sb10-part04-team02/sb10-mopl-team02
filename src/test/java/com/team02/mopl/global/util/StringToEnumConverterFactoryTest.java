package com.team02.mopl.global.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.support.TestSecurityConfiguration;
import com.team02.mopl.support.TestWebMvcConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
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
  void fail_shouldReturnThrowException_whenParamIsEmpty() {
    // given
    StringToEnumConverterFactory factory = new StringToEnumConverterFactory();
    Converter<String, UserSortBy> converter = factory.getConverter(UserSortBy.class);

    // when & then
    assertThrows(BusinessException.class, () -> converter.convert("       "));
  }

  @Test
  @DisplayName("Enum과 매칭되지 않는 값이 오면 예외를 던진다")
  void fail_shouldThrowException_whenValueIsInvalid() {
    // given
    StringToEnumConverterFactory factory = new StringToEnumConverterFactory();
    Converter<String, UserSortBy> converter = factory.getConverter(UserSortBy.class);

    // when & then
    assertThrows(BusinessException.class, () -> converter.convert("invalid value"));
  }

  @Test
  @DisplayName("getValue함수가 없는 일반 Enum은 대소문자가 같지 않으면 예외를 던진다")
  void fail_shouldThrowException_whenEnumHasNoMatchingValue() {
    // given
    StringToEnumConverterFactory factory = new StringToEnumConverterFactory();
    Converter<String, EnumWithoutGetValue> converter =
        factory.getConverter(EnumWithoutGetValue.class);

    // when & then
    assertThrows(BusinessException.class, () -> converter.convert("invalid value"));
  }

  @Test
  @DisplayName("getValue 반환타입이 String이 아니면 예외를 던진다")
  void fail_shouldThrowException_whenGetValueTypeIsNotString() {
    // given
    StringToEnumConverterFactory factory = new StringToEnumConverterFactory();
    Converter<String, IntegerEnum> converter = factory.getConverter(IntegerEnum.class);

    // when & then
    assertThrows(BusinessException.class, () -> converter.convert("invalid value"));
  }

  @Test
  @DisplayName("Reflection invoke함수에 문제가 생기면 예외를 던진다")
  void fail_shouldThrowException_whenReflectionInvokeThrowsException() {
    // given
    StringToEnumConverterFactory factory = new StringToEnumConverterFactory();
    Converter<String, BrokenEnum> converter = factory.getConverter(BrokenEnum.class);

    // when & then
    assertThrows(BusinessException.class, () -> converter.convert("invalid value"));
  }

  // 테스트용 컨트롤러
  @RestController
  static class TestController {

    @GetMapping("/test/convert")
    public ResponseEntity<String> testConvert(
        @RequestParam(value = "sortBy", required = false) UserSortBy sortBy) {
      return ResponseEntity.ok(sortBy != null ? sortBy.name() : "NULL");
    }
  }

  enum EnumWithoutGetValue {
    TEST;
  }

  enum IntegerEnum {
    NUMBER;

    public int getValue() {
      return 99;
    }
  }

  enum BrokenEnum {
    BROKEN_ENUM;

    public String getValue() throws ReflectiveOperationException {
      throw new ReflectiveOperationException();
    }
  }
}
