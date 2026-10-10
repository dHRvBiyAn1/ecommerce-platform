package com.project.authservice.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class CookieUtils {

  public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

  private CookieUtils() {}

  public static void addCookie(
      HttpServletResponse response, String name, String value, int maxAge, boolean secure) {
    Cookie cookie = new Cookie(name, value);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(maxAge);
    cookie.setSecure(secure);
    cookie.setAttribute("SameSite", "Strict");
    response.addCookie(cookie);
  }

  public static void deleteCookie(
      HttpServletRequest request, HttpServletResponse response, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return;
    for (Cookie c : cookies) {
      if (c.getName().equals(name)) {
        response.addHeader(
            org.springframework.http.HttpHeaders.SET_COOKIE,
            org.springframework.http.ResponseCookie.from(name, "")
                .path("/")
                .maxAge(java.time.Duration.ZERO)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .build()
                .toString());
        return;
      }
    }
  }

  public static String getCookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie c : cookies) {
      if (c.getName().equals(name)) return c.getValue();
    }
    return null;
  }
}
