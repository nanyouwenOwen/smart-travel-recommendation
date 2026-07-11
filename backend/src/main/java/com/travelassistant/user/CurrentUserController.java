package com.travelassistant.user;

import com.travelassistant.common.api.ApiResponse;
import com.travelassistant.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class CurrentUserController {
  private final CurrentUserService service;

  public CurrentUserController(CurrentUserService service) {
    this.service = service;
  }

  @GetMapping
  ApiResponse<UserProfile> get(Authentication authentication, HttpServletRequest request) {
    return ApiResponse.of(service.get(authentication.getName()), id(request));
  }

  @PatchMapping
  ApiResponse<UserProfile> update(
      Authentication authentication,
      @Valid @RequestBody UpdateCurrentUserRequest body,
      HttpServletRequest request) {
    return ApiResponse.of(service.update(authentication.getName(), body), id(request));
  }

  private String id(HttpServletRequest request) {
    return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
  }
}
