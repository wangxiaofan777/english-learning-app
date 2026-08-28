package com.lingo.app.common;

public final class UserContext {

  private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

  private UserContext() {
  }

  public static void set(Long userId) {
    USER_ID.set(userId);
  }

  public static Long get() {
    Long id = USER_ID.get();
    if (id == null) {
      throw ApiException.unauthorized("请先登录");
    }
    return id;
  }

  public static void clear() {
    USER_ID.remove();
  }
}
