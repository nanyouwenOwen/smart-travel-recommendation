package com.travelassistant.consultation.ai;

public class ConsultationException extends RuntimeException {
  private final String code;
  private final boolean retryable;

  public ConsultationException(String c, String m, boolean r) {
    super(m);
    code = c;
    retryable = r;
  }

  public String getCode() {
    return code;
  }

  public boolean isRetryable() {
    return retryable;
  }
}
