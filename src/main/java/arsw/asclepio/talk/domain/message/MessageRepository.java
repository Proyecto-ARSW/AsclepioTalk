package arsw.asclepio.talk.domain.message;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
// Daniel Useche
public interface MessageRepository extends JpaRepository<Message, UUID> {

    // Mensajes visibles (no eliminados) de una conversación, paginados y ordenados
    @Query("SELECT m FROM Message m WHERE m.conversationId = :convId AND m.deleted = false ORDER BY m.createdAt ASC")
    Page<Message> findVisibleByConversation(@Param("convId") UUID conversationId, Pageable pageable);
}
