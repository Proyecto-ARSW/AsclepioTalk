package arsw.asclepio.talk.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
// Daniel Useche
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    Optional<MessageAttachment> findFirstByMessageId(UUID messageId);

    // Lookup batch para el enrich del listado (anti N+1).
    List<MessageAttachment> findByMessageIdIn(Collection<UUID> messageIds);
}
