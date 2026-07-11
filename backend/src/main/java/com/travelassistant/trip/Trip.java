package com.travelassistant.trip;

import com.travelassistant.common.persistence.SoftDeletableEntity;
import com.travelassistant.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.SQLRestriction;

@Entity @Table(name="trips") @SQLRestriction("deleted_at IS NULL")
public class Trip extends SoftDeletableEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id") private User user;
    @Column(nullable=false,length=200) private String destination;
    @Column(name="start_date",nullable=false) private LocalDate startDate;
    @Column(nullable=false) private byte days;
    @Column(nullable=false) private short travelers;
    @Column(name="budget_amount",nullable=false,precision=14,scale=2) private BigDecimal budgetAmount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,length=64) private String timezone;
    @Column(name="preferences_json",nullable=false,columnDefinition="json") private String preferencesJson;
    @Column(name="additional_requirements",columnDefinition="text") private String additionalRequirements;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TripStatus status;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="current_version_id") private TripVersion currentVersion;
    @Column(name="failure_code",length=64) private String failureCode;
    protected Trip() {}
    public Trip(User user,String destination,LocalDate startDate,int days,int travelers,BigDecimal budgetAmount,
                String currency,String timezone,String preferencesJson,String requirements) {
        this.user=user; this.destination=destination; this.startDate=startDate; this.days=(byte)days; this.travelers=(short)travelers;
        this.budgetAmount=budgetAmount; this.currency=currency; this.timezone=timezone;
        this.preferencesJson=preferencesJson; this.additionalRequirements=requirements; this.status=TripStatus.GENERATING;
    }
    public void publish(TripVersion version){ currentVersion=version; status=TripStatus.READY; failureCode=null; }
    public void fail(String code){ if(currentVersion==null) status=TripStatus.FAILED; failureCode=code; }
    public void beginGeneration(){ if(currentVersion==null) status=TripStatus.GENERATING; failureCode=null; }
    public void beginAdjustment(){ failureCode=null; }
    public void replaceRequirements(String destination,LocalDate startDate,int days,int travelers,BigDecimal budgetAmount,String currency,String timezone,String preferencesJson,String requirements){this.destination=destination;this.startDate=startDate;this.days=(byte)days;this.travelers=(short)travelers;this.budgetAmount=budgetAmount;this.currency=currency;this.timezone=timezone;this.preferencesJson=preferencesJson;this.additionalRequirements=requirements;}
    public User getUser(){return user;} public String getDestination(){return destination;} public LocalDate getStartDate(){return startDate;}
    public int getDays(){return days;} public int getTravelers(){return travelers;} public BigDecimal getBudgetAmount(){return budgetAmount;}
    public String getCurrency(){return currency;} public String getTimezone(){return timezone;} public String getPreferencesJson(){return preferencesJson;}
    public String getAdditionalRequirements(){return additionalRequirements;} public TripStatus getStatus(){return status;}
    public TripVersion getCurrentVersion(){return currentVersion;} public String getFailureCode(){return failureCode;}
}
