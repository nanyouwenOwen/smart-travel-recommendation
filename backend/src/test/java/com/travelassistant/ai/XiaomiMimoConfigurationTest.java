package com.travelassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelassistant.consultation.ai.*;
import com.travelassistant.trip.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

class XiaomiMimoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              XiaomiMimoConfiguration.class,
              TripPlanningAiConfiguration.class,
              ConsultationAiConfiguration.class,
              XiaomiMimoAnthropicTripPlanningProvider.class,
              XiaomiMimoAnthropicConsultationProvider.class,
              MapperConfiguration.class)
          .withPropertyValues(
              "app.ai.xiaomi-mimo.base-url=https://token-plan-cn.xiaomimimo.com/anthropic",
              "app.ai.xiaomi-mimo.api-key=fake-test-only",
              "app.ai.xiaomi-mimo.model=mimo-v2.5",
              "app.ai.xiaomi-mimo.anthropic-version=2023-06-01",
              "app.ai.xiaomi-mimo.max-output-tokens=8192",
              "app.trip-planning.prompt-version=trip-planner/v1",
              "app.trip-planning.connect-timeout=PT1S",
              "app.trip-planning.request-timeout=PT2S",
              "app.trip-planning.total-timeout=PT3S",
              "app.trip-planning.max-attempts=1",
              "app.trip-planning.global-concurrency=1",
              "app.trip-planning.user-requests-per-minute=1",
              "app.consultation.global-concurrency=1",
              "app.consultation.user-rpm=1",
              "app.consultation.user-concurrency=1",
              "app.consultation.request-timeout=PT2S",
              "app.consultation.total-timeout=PT3S",
              "app.consultation.first-byte-timeout=PT1S",
              "app.consultation.idle-timeout=PT1S",
              "app.consultation.max-attempts=1",
              "app.consultation.event-retention=PT1M",
              "app.consultation.disconnect-grace=PT1S");

  @Test
  void createsOnlySelectedProviders() {
    runner
        .withPropertyValues(
            "app.trip-planning.provider=xiaomi-mimo-anthropic", "app.consultation.provider=stub")
        .run(
            context -> {
              assertThat(context).hasSingleBean(XiaomiMimoAnthropicTripPlanningProvider.class);
              assertThat(context).doesNotHaveBean(XiaomiMimoAnthropicConsultationProvider.class);
            });
    runner
        .withPropertyValues(
            "app.trip-planning.provider=xiaomi-mimo-anthropic",
            "app.consultation.provider=xiaomi-mimo-anthropic")
        .run(
            context -> {
              assertThat(context).hasSingleBean(XiaomiMimoAnthropicTripPlanningProvider.class);
              assertThat(context).hasSingleBean(XiaomiMimoAnthropicConsultationProvider.class);
            });
    runner
        .withPropertyValues(
            "app.trip-planning.provider=stub", "app.consultation.provider=xiaomi-mimo-anthropic")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(XiaomiMimoAnthropicTripPlanningProvider.class);
              assertThat(context).hasSingleBean(XiaomiMimoAnthropicConsultationProvider.class);
            });
  }

  @Test
  void missingKeyFailsOnlyWhenMimoSelected() {
    runner
        .withPropertyValues(
            "app.ai.xiaomi-mimo.api-key=",
            "app.trip-planning.provider=xiaomi-mimo-anthropic",
            "app.consultation.provider=stub")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues(
            "app.ai.xiaomi-mimo.api-key=",
            "app.trip-planning.provider=stub",
            "app.consultation.provider=xiaomi-mimo-anthropic")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues(
            "app.ai.xiaomi-mimo.api-key=",
            "app.trip-planning.provider=stub",
            "app.consultation.provider=stub")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MapperConfiguration {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
