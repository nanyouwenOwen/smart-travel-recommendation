package com.travelassistant.trip;
import jakarta.persistence.LockModeType; import java.util.*; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface TripRepository extends JpaRepository<Trip,String> {
 Optional<Trip> findByIdAndUserId(String id,String userId);
 @EntityGraph(attributePaths="currentVersion") Page<Trip> findByUserIdOrderByCreatedAtDescIdDesc(String userId,Pageable pageable);
 @EntityGraph(attributePaths="currentVersion") @Query("select t from Trip t where t.user.id=:userId and (t.createdAt<:createdAt or (t.createdAt=:createdAt and t.id<:id)) order by t.createdAt desc,t.id desc") List<Trip> findAfter(@Param("userId")String userId,@Param("createdAt")java.time.Instant createdAt,@Param("id")String id,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from Trip t where t.id=:id and t.user.id=:userId") Optional<Trip> lockOwned(@Param("id")String id,@Param("userId")String userId);
 @Query(value="select user_id from trips where id=:id",nativeQuery=true) Optional<String> findOwnerIncludingDeleted(@Param("id")String id);
}
