package com.lingo.app.common;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

  private final int code;
  private final transient Object payload;

  public ApiException(int code, String message) {
    this(code, message, null);
  }

  public ApiException(int code, String message, Object payload) {
    super(message);
    this.code = code;
    this.payload = payload;
  }

  public static ApiException badRequest(String message) {
    return new ApiException(400, message);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(401, message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(404, message);
  }
}
