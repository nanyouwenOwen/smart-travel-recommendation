package com.travelassistant.trip;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.LocalTime; import java.util.UUID;
@Entity @Table(name="activities",uniqueConstraints=@UniqueConstraint(columnNames={"itinerary_day_id","sequence_number"}))
public class Activity {
 @Id @Column(length=36) private String id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="itinerary_day_id") private ItineraryDay itineraryDay;
 @Column(name="sequence_number",nullable=false) private short sequenceNumber; @Column(name="start_time",nullable=false) private LocalTime startTime; @Column(name="end_time",nullable=false) private LocalTime endTime;
 @Column(nullable=false,length=200) private String title; @Column(nullable=false,length=300) private String location; @Column(columnDefinition="text") private String description;
 @Column(name="estimated_cost",nullable=false,precision=14,scale=2) private BigDecimal estimatedCost; @Column(nullable=false,length=3) private String currency;
 @Enumerated(EnumType.STRING) @Column(name="budget_category",nullable=false,length=32) private BudgetCategory category; @Column(name="transport_advice",columnDefinition="text") private String transportAdvice;
 protected Activity(){} public Activity(ItineraryDay day,int seq,LocalTime start,LocalTime end,String title,String location,String description,BigDecimal cost,String currency,BudgetCategory category,String transport){itineraryDay=day;sequenceNumber=(short)seq;startTime=start;endTime=end;this.title=title;this.location=location;this.description=description;estimatedCost=cost;this.currency=currency;this.category=category;transportAdvice=transport;}
 @PrePersist void init(){if(id==null)id=UUID.randomUUID().toString();} public int getSequenceNumber(){return sequenceNumber;} public LocalTime getStartTime(){return startTime;} public LocalTime getEndTime(){return endTime;} public String getTitle(){return title;} public String getLocation(){return location;} public String getDescription(){return description;} public BigDecimal getEstimatedCost(){return estimatedCost;} public String getCurrency(){return currency;} public BudgetCategory getCategory(){return category;} public String getTransportAdvice(){return transportAdvice;}
}
