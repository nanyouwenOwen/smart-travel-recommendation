package com.travelassistant.trip;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="trip_versions",uniqueConstraints=@UniqueConstraint(columnNames={"trip_id","version_number"}))
public class TripVersion {
    @Id @Column(length=36) private String id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="trip_id") private Trip trip;
    @Column(name="version_number",nullable=false) private int versionNumber;
    @Enumerated(EnumType.STRING) @Column(name="generation_type",nullable=false,length=16) private GenerationType generationType;
    @Column(name="adjustment_instruction",columnDefinition="text") private String adjustmentInstruction;
    @Column(name="estimated_total",precision=14,scale=2) private BigDecimal estimatedTotal;
    @Column(name="budget_amount",nullable=false,precision=14,scale=2) private BigDecimal budgetAmount;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="warnings_json",nullable=false,columnDefinition="json") private String warningsJson;
    @Column(name="source_updated_at") private Instant sourceUpdatedAt;
    @Column(name="prompt_version",nullable=false,length=64) private String promptVersion;
    @Column(nullable=false,length=64) private String provider;
    @Column(nullable=false,length=100) private String model;
    @Column(name="input_tokens") private Integer inputTokens; @Column(name="output_tokens") private Integer outputTokens;
    @Column(name="generation_duration_ms") private Long generationDurationMs;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @OneToMany(mappedBy="tripVersion",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("dayNumber") private List<ItineraryDay> itineraryDays=new ArrayList<>();
    protected TripVersion() {}
    public TripVersion(Trip trip,int number,GenerationType type,BigDecimal total,BigDecimal budgetAmount,String currency,String warnings,
                       String promptVersion,String provider,String model){this.trip=trip;versionNumber=number;generationType=type;
        estimatedTotal=total;this.budgetAmount=budgetAmount;this.currency=currency;warningsJson=warnings;this.promptVersion=promptVersion;this.provider=provider;this.model=model;}
    @PrePersist void init(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=Instant.now();}
    public void addDay(ItineraryDay day){itineraryDays.add(day);}
    public void setAdjustmentInstruction(String instruction){this.adjustmentInstruction=instruction;}
    public void setGenerationDurationMs(long duration){this.generationDurationMs=duration;}
    public String getId(){return id;} public int getVersionNumber(){return versionNumber;} public BigDecimal getEstimatedTotal(){return estimatedTotal;}
    public String getCurrency(){return currency;} public String getWarningsJson(){return warningsJson;} public Instant getCreatedAt(){return createdAt;}
    public BigDecimal getBudgetAmount(){return budgetAmount;}
    public List<ItineraryDay> getItineraryDays(){return List.copyOf(itineraryDays);} public Trip getTrip(){return trip;}
}
