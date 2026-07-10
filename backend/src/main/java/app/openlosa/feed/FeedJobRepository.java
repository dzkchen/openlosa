package app.openlosa.feed;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FeedJobRepository extends JpaRepository<FeedJob, Long> {

    @Query("select job.engineId from FeedJob job where job.open = true")
    Set<String> findOpenEngineIds();

    List<FeedJob> findByOpenTrue();

    List<FeedJob> findByOpenTrueOrEngineIdIn(Collection<String> engineIds);
}
