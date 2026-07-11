package com.travelassistant.trip;
import jakarta.persistence.*; import java.time.LocalDate; import java.util.*;
@Entity @Table(name="itinerary_days",uniqueConstraints=@UniqueConstraint(columnNames={"trip_version_id","day_number"}))
public class ItineraryDay {
 @Id @Column(length=36) private String id; @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="trip_version_id") private TripVersion tripVersion;
 @Column(name="day_number",nullable=false) private byte dayNumber; @Column(name="itinerary_date",nullable=false) private LocalDate date; @Column(length=500) private String summary;
 @OneToMany(mappedBy="itineraryDay",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("sequenceNumber") private List<Activity> activities=new ArrayList<>();
 protected ItineraryDay(){} public ItineraryDay(TripVersion version,int number,LocalDate date,String summary){tripVersion=version;dayNumber=(byte)number;this.date=date;this.summary=summary;}
 @PrePersist void init(){if(id==null)id=UUID.randomUUID().toString();} public void addActivity(Activity a){activities.add(a);}
 public int getDayNumber(){return dayNumber;} public LocalDate getDate(){return date;} public String getSummary(){return summary;} public List<Activity> getActivities(){return List.copyOf(activities);}
}
