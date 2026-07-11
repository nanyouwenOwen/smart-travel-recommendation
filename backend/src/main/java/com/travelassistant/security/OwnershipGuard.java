package com.travelassistant.security;

import com.travelassistant.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OwnershipGuard {
    public void requireOwner(String authenticatedUserId, String resourceOwnerId) {
        if (authenticatedUserId == null || !authenticatedUserId.equals(resourceOwnerId)) {
            throw new BusinessException("RESOURCE_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND);
        }
    }
}
