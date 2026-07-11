package com.travelassistant.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.AssertTrue;

@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank @Size(min = 32) String jwtSecret) {
    @AssertTrue(message = "Token 有效期必须为正数")
    public boolean isTokenTtlValid() {
        return !accessTokenTtl.isZero() && !accessTokenTtl.isNegative()
                && !refreshTokenTtl.isZero() && !refreshTokenTtl.isNegative();
    }
}
