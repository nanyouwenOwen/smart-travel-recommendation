package com.travelassistant.trip.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelassistant.common.exception.BusinessException;
import com.travelassistant.trip.BudgetCategory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TripPlanningCoreTest {
    private final TripPlanValidator validator = new TripPlanValidator();

    @Test
    void stubProducesValidOneAndThirtyDayPlans() {
        StubTripPlanningProvider provider = new StubTripPlanningProvider();
        for (int days : List.of(1, 30)) {
            TripPlanningRequest request = request(days, new BigDecimal("6000.00"));
            TripPlan plan = provider.generate(request);
            validator.validate(request, plan);
            assertThat(plan.days()).hasSize(days);
        }
    }

    @Test
    void rejectsOverlapsAndCalculatesBudgetCategories() {
        TripPlanningRequest request = request(1, new BigDecimal("100.00"));
        PlannedActivity first = activity(1, 9, 11, "70.00", BudgetCategory.FOOD);
        PlannedActivity overlap = activity(2, 10, 12, "50.00", BudgetCategory.ATTRACTION);
        TripPlan invalid = new TripPlan(List.of(new PlannedDay(1, request.startDate(), "", List.of(first, overlap))), List.of());
        assertThatThrownBy(() -> validator.validate(request, invalid))
                .isInstanceOf(TripPlanningException.class).hasMessageContaining("重叠");

        TripPlan valid = new TripPlan(List.of(new PlannedDay(1, request.startDate(), "",
                List.of(first, activity(2, 11, 12, "50.00", BudgetCategory.ATTRACTION)))), List.of());
        BudgetCalculator.BudgetResult result = new BudgetCalculator().calculate(valid, request.budget());
        assertThat(result.total()).isEqualByComparingTo("120.00");
        assertThat(result.exceedsBudget()).isTrue();
        assertThat(result.exceededBy()).isEqualByComparingTo("20.00");
    }

    @Test
    void gatewayRetriesTransientFailureAndEnforcesPerUserRate() {
        AtomicInteger calls = new AtomicInteger();
        TripPlanningProvider provider = new TripPlanningProvider() {
            public TripPlan generate(TripPlanningRequest request) {
                if (calls.incrementAndGet() == 1) throw new TripPlanningException("AI_TIMEOUT", "timeout", true);
                return new StubTripPlanningProvider().generate(request);
            }
            public String providerName() { return "fake"; }
            public String modelName() { return "fake-v1"; }
        };
        TripPlanningProperties properties = new TripPlanningProperties("fake", "v1", Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(3), 2, 1, 1);
        TripPlanningGateway gateway = new TripPlanningGateway(provider, properties, validator);
        gateway.admit("user");
        assertThat(gateway.generate("user", request(1, new BigDecimal("100.00"))).days()).hasSize(1);
        assertThat(calls).hasValue(2);
        assertThatThrownBy(() -> gateway.admit("user"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void enforcesStrictTotalDeadlineAroundSlowProvider() {
        TripPlanningProvider slow = new TripPlanningProvider() {
            public TripPlan generate(TripPlanningRequest request) {
                try { Thread.sleep(2_000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                return new StubTripPlanningProvider().generate(request);
            }
            public String providerName() { return "slow"; }
            public String modelName() { return "slow-v1"; }
        };
        TripPlanningProperties properties = new TripPlanningProperties("slow", "v1", Duration.ofMillis(10),
                Duration.ofMillis(100), Duration.ofMillis(100), 1, 1, 10);
        TripPlanningGateway gateway = new TripPlanningGateway(slow, properties, validator);
        Instant started = Instant.now();
        assertThatThrownBy(() -> gateway.generate("deadline-user", request(1, new BigDecimal("100.00"))))
                .isInstanceOf(TripPlanningException.class).hasMessageContaining("截止时间");
        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofMillis(500));
    }

    private TripPlanningRequest request(int days, BigDecimal budget) {
        return new TripPlanningRequest("杭州", LocalDate.of(2030, 1, 1), days, 2, budget,
                "CNY", "Asia/Shanghai", List.of("文化"), null, null, null);
    }

    private PlannedActivity activity(int sequence, int start, int end, String cost, BudgetCategory category) {
        return new PlannedActivity(sequence, LocalTime.of(start, 0), LocalTime.of(end, 0), "活动", "地点",
                "说明", new BigDecimal(cost), category, "步行");
    }
}
