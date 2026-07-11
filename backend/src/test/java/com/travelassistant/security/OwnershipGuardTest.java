package com.travelassistant.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelassistant.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class OwnershipGuardTest {
    private final OwnershipGuard guard = new OwnershipGuard();

    @Test
    void allowsOwnerAndHidesForeignResource() {
        assertThatCode(() -> guard.requireOwner("user-1", "user-1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireOwner("user-1", "user-2"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("RESOURCE_NOT_FOUND");
    }
}
