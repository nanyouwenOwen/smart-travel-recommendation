package com.travelassistant.consultation.security;

public class UnsafeContentException extends RuntimeException {
  private final String code;

  public UnsafeContentException(String c, String m) {
    super(m);
    code = c;
  }

  public String getCode() {
    return code;
  }
}
