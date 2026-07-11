package com.travelassistant.auth;

import com.travelassistant.common.exception.BusinessException;
import com.travelassistant.user.User;
import com.travelassistant.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final TokenService tokens;

  public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
    this.users = users;
    this.passwords = passwords;
    this.tokens = tokens;
  }

  @Transactional
  public TokenPair register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    String name = normalizeName(request.displayName());
    validatePasswordBytes(request.password());
    if (users.existsByEmailIgnoreCase(email)) throw duplicateEmail();
    try {
      User user = users.saveAndFlush(new User(email, passwords.encode(request.password()), name));
      return tokens.issue(user);
    } catch (DataIntegrityViolationException e) {
      throw duplicateEmail();
    }
  }

  @Transactional(readOnly = true)
  public User authenticate(LoginRequest request) {
    User user = users.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
    if (user == null
        || request.password().getBytes(StandardCharsets.UTF_8).length > 72
        || !passwords.matches(request.password(), user.getPasswordHash()))
      throw new BusinessException("INVALID_CREDENTIALS", "邮箱或密码错误", HttpStatus.UNAUTHORIZED);
    return user;
  }

  public TokenPair login(LoginRequest request) {
    return tokens.issue(authenticate(request));
  }

  private String normalizeEmail(String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches())
      throw new BusinessException("VALIDATION_ERROR", "邮箱格式不正确", HttpStatus.BAD_REQUEST);
    return normalized;
  }

  public static String normalizeName(String name) {
    String normalized = name.trim();
    if (normalized.isEmpty())
      throw new BusinessException("VALIDATION_ERROR", "展示名称不能为空", HttpStatus.BAD_REQUEST);
    return normalized;
  }

  private BusinessException duplicateEmail() {
    return new BusinessException("EMAIL_ALREADY_REGISTERED", "该邮箱已注册", HttpStatus.CONFLICT);
  }

  private void validatePasswordBytes(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException(
          "VALIDATION_ERROR", "密码 UTF-8 编码后不能超过 72 字节", HttpStatus.BAD_REQUEST);
  }
}
