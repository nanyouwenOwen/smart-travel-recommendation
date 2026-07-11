package com.travelassistant.consultation;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface ConversationStreamEventRepository extends JpaRepository<ConversationStreamEvent,String>{List<ConversationStreamEvent>findByStreamIdAndSequenceGreaterThanOrderBySequence(String stream,int after);long deleteByExpiresAtBefore(java.time.Instant cutoff);}
