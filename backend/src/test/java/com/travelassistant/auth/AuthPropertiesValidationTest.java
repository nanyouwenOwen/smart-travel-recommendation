package com.travelassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthPropertiesValidationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues("app.auth.issuer=test", "app.auth.jwt-secret=12345678901234567890123456789012");

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S"})
    void rejectsNonPositiveAccessTtl(String ttl) {
        runner.withPropertyValues("app.auth.access-token-ttl=" + ttl, "app.auth.refresh-token-ttl=PT1H")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S"})
    void rejectsNonPositiveRefreshTtl(String ttl) {
        runner.withPropertyValues("app.auth.access-token-ttl=PT1M", "app.auth.refresh-token-ttl=" + ttl)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    static class PropertiesConfiguration {}
}
