package app.openlosa.contact;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, Long>, JpaSpecificationExecutor<Contact> {

    @Override
    @EntityGraph(attributePaths = "company")
    List<Contact> findAll(Specification<Contact> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = "company")
    Page<Contact> findAll(Specification<Contact> spec, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Contact contact
        set contact.lastContactedAt = :sentAt
        where contact.id = :contactId
          and (contact.lastContactedAt is null or contact.lastContactedAt < :sentAt)
        """)
    int advanceLastContactedAt(@Param("contactId") Long contactId, @Param("sentAt") LocalDate sentAt);
}
