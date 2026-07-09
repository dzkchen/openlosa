package app.openlosa.prospect;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProspectRepository extends JpaRepository<Prospect, Long>, JpaSpecificationExecutor<Prospect> {

    @Override
    @EntityGraph(attributePaths = {"tags", "promotedApplication", "promotedApplication.company"})
    List<Prospect> findAll(Specification<Prospect> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"tags", "promotedApplication", "promotedApplication.company"})
    Optional<Prospect> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prospect p where p.id = :id")
    @EntityGraph(attributePaths = {"tags", "promotedApplication", "promotedApplication.company"})
    Optional<Prospect> findByIdForUpdate(@Param("id") Long id);
}
