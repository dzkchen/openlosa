package app.openlosa.outreach;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OutreachRepository extends JpaRepository<Outreach, Long>, JpaSpecificationExecutor<Outreach> {

    @Override
    @EntityGraph(attributePaths = {"contact", "contact.company", "company", "application", "application.company"})
    List<Outreach> findAll(Specification<Outreach> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"contact", "contact.company", "company", "application", "application.company"})
    Page<Outreach> findAll(Specification<Outreach> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"contact", "contact.company", "company", "application", "application.company"})
    Optional<Outreach> findById(Long id);
}
