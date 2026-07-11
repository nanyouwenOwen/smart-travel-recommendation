package com.travelassistant.realtime.cache;
import com.travelassistant.common.persistence.AuditableEntity; import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="external_data_cache")
public class ExternalDataCache extends AuditableEntity {
 @Column(name="cache_key",nullable=false,unique=true,length=64) private String cacheKey; @Column(nullable=false,length=40) private String provider;
 @Column(name="data_type",nullable=false,length=32) private String dataType; @Column(nullable=false,columnDefinition="json") private String payload;
 @Column(name="source_updated_at") private Instant sourceUpdatedAt; @Column(name="fetched_at",nullable=false) private Instant fetchedAt;
 @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="stale_until",nullable=false) private Instant staleUntil;
 @Column(name="last_failure_code",length=64) private String lastFailureCode;
 protected ExternalDataCache(){} public ExternalDataCache(String key,String provider,String type,String payload,Instant sourceUpdatedAt,Instant fetchedAt,Instant expiresAt,Instant staleUntil){this.cacheKey=key;this.provider=provider;this.dataType=type;this.payload=payload;this.sourceUpdatedAt=sourceUpdatedAt;this.fetchedAt=fetchedAt;this.expiresAt=expiresAt;this.staleUntil=staleUntil;}
 public void refresh(String value,Instant source,Instant fetched,Instant expires,Instant stale){payload=value;sourceUpdatedAt=source;fetchedAt=fetched;expiresAt=expires;staleUntil=stale;lastFailureCode=null;}
 public void fail(String code){lastFailureCode=code;} public String getPayload(){return payload;} public Instant getFetchedAt(){return fetchedAt;} public Instant getExpiresAt(){return expiresAt;} public Instant getStaleUntil(){return staleUntil;} public Instant getSourceUpdatedAt(){return sourceUpdatedAt;}
}
