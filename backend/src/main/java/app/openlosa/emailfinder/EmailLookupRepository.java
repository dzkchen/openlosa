package app.openlosa.emailfinder;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface EmailLookupRepository extends JpaRepository<EmailLookup, Long> {

    @EntityGraph(attributePaths = {"contact", "contact.company", "chosenOutreach"})
    List<EmailLookup> findTop5ByContactIdOrderByCreatedAtDesc(Long contactId);

    @Override
    @EntityGraph(attributePaths = {"contact", "contact.company", "chosenOutreach"})
    Optional<EmailLookup> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lookup from EmailLookup lookup where lookup.id = :id")
    @EntityGraph(attributePaths = {"contact", "contact.company", "chosenOutreach"})
    Optional<EmailLookup> findByIdForUpdate(@Param("id") Long id);
}
