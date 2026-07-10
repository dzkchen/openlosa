package app.openlosa.feed;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface FeedIngestRunRepository extends JpaRepository<FeedIngestRun, Long> {

    Optional<FeedIngestRun> findFirstByFileFingerprintIsNotNullAndStatusInOrderByIdDesc(
        Collection<FeedIngestStatus> statuses
    );
}
