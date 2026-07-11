package com.travelassistant.realtime.location;
import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface LocationReferenceRepository extends JpaRepository<LocationReference,String> {
    Optional<LocationReference> findByProviderAndProviderRef(String provider,String providerRef);
}
