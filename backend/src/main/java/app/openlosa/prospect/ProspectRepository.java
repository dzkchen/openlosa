package app.openlosa.prospect;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProspectRepository extends JpaRepository<Prospect, Long>, JpaSpecificationExecutor<Prospect> {

    @Override
    @EntityGraph(attributePaths = {"tags", "promotedApplication", "promotedApplication.company"})
    List<Prospect> findAll(Specification<Prospect> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"tags", "promotedApplication", "promotedApplication.company"})
    Optional<Prospect> findById(Long id);
}
