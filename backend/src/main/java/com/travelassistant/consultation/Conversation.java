package com.travelassistant.consultation;

import com.travelassistant.common.persistence.SoftDeletableEntity;
import com.travelassistant.trip.Trip;
import com.travelassistant.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "conversations")
@SQLRestriction("deleted_at IS NULL")
public class Conversation extends SoftDeletableEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @Column(length = 100)
  private String title;

  protected Conversation() {}

  public Conversation(User u, Trip t, String title) {
    user = u;
    trip = t;
    this.title = title;
  }

  public void titleIfMissing(String value) {
    if (title == null) title = value;
  }

  public User getUser() {
    return user;
  }

  public Trip getTrip() {
    return trip;
  }

  public String getTitle() {
    return title;
  }
}
