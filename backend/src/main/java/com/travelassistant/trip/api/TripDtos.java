package com.travelassistant.trip.api;
import com.travelassistant.trip.*; import java.util.List;
public final class TripDtos {
 private TripDtos(){}
 public record Money(String amount,String currency){}
 public record ActivityView(int sequenceNumber,String startTime,String endTime,String title,String location,String description,Money estimatedCost,BudgetCategory category,String transportAdvice){}
 public record DayView(int dayNumber,String date,String summary,List<ActivityView> activities){}
 public record CategoryAmount(BudgetCategory category,Money amount){}
 public record BudgetBreakdown(List<CategoryAmount> categories,Money total,boolean exceedsBudget,Money exceededBy){}
 public record TripView(String id,String destination,String startDate,int days,int travelers,Money budget,Money estimatedTotal,String currency,String timezone,List<String> preferences,String additionalRequirements,TripStatus status,Integer currentVersion,String failureCode,List<String> warnings,BudgetBreakdown budgetBreakdown,List<DayView> itinerary,String createdAt){}
 public record TripSummary(String id,String destination,String startDate,int days,TripStatus status,Integer currentVersion,String failureCode,String createdAt){}
 public record VersionSummary(int versionNumber,Money estimatedTotal,String createdAt){}
 public record VersionView(int versionNumber,Money estimatedTotal,List<String> warnings,BudgetBreakdown budgetBreakdown,List<DayView> itinerary,String createdAt){}
 public record PageResult<T>(List<T> items,String nextCursor,boolean hasMore){}
}
