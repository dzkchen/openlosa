package app.openlosa.feed;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface FeedJobRepository extends JpaRepository<FeedJob, Long>, JpaSpecificationExecutor<FeedJob> {

    @Query("select job.engineId from FeedJob job where job.open = true")
    Set<String> findOpenEngineIds();

    long countByOpenTrueAndHiddenFalse();

    List<FeedJob> findByOpenTrue();

    List<FeedJob> findByOpenTrueOrEngineIdIn(Collection<String> engineIds);

    // Pessimistic row lock so a double-clicked save/create action serialises on the feed
    // job row and cannot create a second prospect/application before the FK link is written.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from FeedJob job where job.id = :id")
    Optional<FeedJob> findByIdForUpdate(@Param("id") Long id);
}
