package com.team02.mopl.global.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.SerializationUtils;

public class CookieUtils {

  public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null && cookies.length > 0) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(name)) {
          return Optional.of(cookie);
        }
      }
    }
    return Optional.empty();
  }

  public static void addCookie(
      HttpServletResponse response, String name, String value, int maxAge) {
    ResponseCookie cookie =
        ResponseCookie.from(name, value)
            .path("/")
            .maxAge(maxAge)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax") // OAuth2 리다이렉트 호환성을 위한 Lax 설정
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public static void deleteCookie(
      HttpServletRequest request, HttpServletResponse response, String name) {
    ResponseCookie cookie = ResponseCookie.from(name, "").path("/").maxAge(0).build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public static String serialize(Object object) {
    return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(object));
  }

  public static <T> T deserialize(Cookie cookie, Class<T> cls) {
    byte[] bytes = Base64.getUrlDecoder().decode(cookie.getValue());
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      // OAuth2 관련 클래스와 기본 패키지만 허용
      ObjectInputFilter filter =
          ObjectInputFilter.Config.createFilter(
              "org.springframework.security.oauth2.core.**;"
                  + "java.lang.*;"
                  + "java.util.*;"
                  + "!*");
      ois.setObjectInputFilter(filter);
      return cls.cast(ois.readObject());
    } catch (Exception e) {
      throw new IllegalArgumentException("Cookie deserialization failed", e);
    }
  }
}
