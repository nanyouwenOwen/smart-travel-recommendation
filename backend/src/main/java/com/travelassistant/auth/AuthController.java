package com.travelassistant.auth;

import com.travelassistant.common.api.ApiResponse;
import com.travelassistant.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth; private final TokenService tokens;
    public AuthController(AuthService auth, TokenService tokens) { this.auth=auth; this.tokens=tokens; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest req) {
        return ApiResponse.of(AuthResponse.from(auth.register(body)), id(req));
    }
    @PostMapping("/login")
    ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest req) {
        return ApiResponse.of(AuthResponse.from(auth.login(body)), id(req));
    }
    @PostMapping("/refresh")
    ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest body, HttpServletRequest req) {
        return ApiResponse.of(AuthResponse.from(tokens.rotate(body.refreshToken())), id(req));
    }
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest body) {
        tokens.revoke(body.refreshToken()); return ResponseEntity.noContent().build();
    }
    private String id(HttpServletRequest req) { return (String) req.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
