package com.travelassistant.user;

import com.travelassistant.auth.AuthService;
import com.travelassistant.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {
    private final UserRepository users;
    public CurrentUserService(UserRepository users) { this.users=users; }
    @Transactional(readOnly=true)
    public UserProfile get(String id) { return UserProfile.from(require(id)); }
    @Transactional
    public UserProfile update(String id, UpdateCurrentUserRequest request) {
        User user=require(id); user.changeDisplayName(AuthService.normalizeName(request.displayName()));
        return UserProfile.from(user);
    }
    private User require(String id) {
        return users.findById(id).orElseThrow(() -> new BusinessException(
                "UNAUTHORIZED", "需要有效的身份认证", HttpStatus.UNAUTHORIZED));
    }
}
