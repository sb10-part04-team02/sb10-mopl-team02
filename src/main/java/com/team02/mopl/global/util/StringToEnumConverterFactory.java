package com.team02.mopl.global.util;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.lang.reflect.InvocationTargetException;
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

    public StringToEnumConverter(Class<T> enumType) {
      this.enumType = enumType;
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

      try {
        Method getValueMethod = enumType.getMethod("getValue");
        for (T enumConstant : enumType.getEnumConstants()) {
          String value = (String) getValueMethod.invoke(enumConstant);
          // Enum getValue()함수 활용. 필드 내부의 값과 비교
          if (value != null && value.equalsIgnoreCase(source)) {
            return enumConstant;
          }
        }
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        // getValue() 메서드가 없는 일반 Enum은 통과
      }

      throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE);
    }
  }
}
