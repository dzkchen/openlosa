package app.openlosa.application;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusTransitionRepository extends JpaRepository<StatusTransition, Long> {

    List<StatusTransition> findByApplicationIdOrderByOccurredAtAscIdAsc(Long applicationId);

    Optional<StatusTransition> findFirstByApplicationIdOrderByOccurredAtDescIdDesc(Long applicationId);
}
