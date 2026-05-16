package arsw.asclepio.talk.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
// Daniel Useche
public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {

    // Batch fetch — evita N+1 al construir el listado de mensajes.
    @Query("SELECT r FROM MessageReaction r WHERE r.messageId IN :messageIds")
    List<MessageReaction> findByMessageIds(@Param("messageIds") Collection<UUID> messageIds);

    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

    // Delete dirigido por (mensaje, usuario, emoji) — el caller no necesita
    // conocer el UUID artificial de la fila.
    @Modifying
    @Transactional
    long deleteByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);

    List<MessageReaction> findByMessageId(UUID messageId);
}
