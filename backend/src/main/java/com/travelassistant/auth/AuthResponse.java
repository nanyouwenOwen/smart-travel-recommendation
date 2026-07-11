package com.travelassistant.auth;
public record AuthResponse(String accessToken, String refreshToken, long expiresIn, String tokenType) {
    public static AuthResponse from(TokenPair pair) {
        return new AuthResponse(pair.accessToken(), pair.refreshToken(), pair.expiresIn(), "Bearer");
    }
}
