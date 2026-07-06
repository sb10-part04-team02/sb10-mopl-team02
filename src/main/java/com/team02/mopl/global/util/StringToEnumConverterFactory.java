package com.team02.mopl.global.util;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

@Slf4j
@SuppressWarnings({"rawtypes"})
public class StringToEnumConverterFactory implements ConverterFactory<String, Enum> {

  @Override
  public <T extends Enum> Converter<String, T> getConverter(@NonNull Class<T> targetType) {
    return new StringToEnumConverter<>(targetType);
  }

  private static final class StringToEnumConverter<T extends Enum> implements Converter<String, T> {
    private final Class<T> enumType;
    private final Method valueMethod; // method 캐싱용

    public StringToEnumConverter(Class<T> enumType) {
      this.enumType = enumType;

      Method method = null;
      try {
        method = enumType.getMethod("getValue");
      } catch (NoSuchMethodException e) {
        // getValue가 없는 enum은 null로 유지
      }
      this.valueMethod = method;
    }

    @Override
    public T convert(@NonNull String source) {
      if (source.isBlank()) {
        throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE);
      }

      for (T enumConstant : enumType.getEnumConstants()) {
        // 대소문자 무시하고 확인
        if (enumConstant.name().equalsIgnoreCase(source)) {
          return enumConstant;
        }
      }

      // 캐싱된 메서드 있을때만 실행
      if (valueMethod != null) {
        try {
          for (T enumConstant : enumType.getEnumConstants()) {
            Object value = valueMethod.invoke(enumConstant);
            // Enum getValue()함수 활용. 필드 내부의 값과 비교
            if (value instanceof String stringValue && stringValue.equalsIgnoreCase(source)) {
              return enumConstant;
            }
          }
        } catch (ReflectiveOperationException e) {
          log.warn("getValue() 호출 중 예외 발생: enumType={}", enumType, e);
        }
      }

      throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE);
    }
  }
}
