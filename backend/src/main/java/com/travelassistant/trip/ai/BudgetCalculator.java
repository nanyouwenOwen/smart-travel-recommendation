package com.travelassistant.trip.ai;
import com.travelassistant.trip.BudgetCategory; import java.math.BigDecimal; import java.util.*; import org.springframework.stereotype.Component;
@Component public class BudgetCalculator {
 public BudgetResult calculate(TripPlan plan,BigDecimal budget){EnumMap<BudgetCategory,BigDecimal> categories=new EnumMap<>(BudgetCategory.class); for(BudgetCategory c:BudgetCategory.values())categories.put(c,BigDecimal.ZERO.setScale(2));
  plan.days().stream().flatMap(d->d.activities().stream()).forEach(a->categories.merge(a.category(),a.estimatedCost(),BigDecimal::add));
  BigDecimal total=categories.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2); return new BudgetResult(Map.copyOf(categories),total,total.compareTo(budget)>0,total.subtract(budget).max(BigDecimal.ZERO).setScale(2));}
 public record BudgetResult(Map<BudgetCategory,BigDecimal> categories,BigDecimal total,boolean exceedsBudget,BigDecimal exceededBy){}
}
