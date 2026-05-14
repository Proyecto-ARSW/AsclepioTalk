package arsw.asclepio.talk.domain.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
// Daniel Useche
public interface ParticipantRepository extends JpaRepository<Participant, ParticipantId> {

    @Query("SELECT p FROM Participant p WHERE p.id.conversationId = :convId AND p.active = true")
    List<Participant> findActiveByConversation(@Param("convId") UUID conversationId);

    @Query("SELECT p FROM Participant p WHERE p.id.conversationId = :convId AND p.id.userId = :userId")
    Optional<Participant> findByConversationAndUser(
            @Param("convId") UUID conversationId,
            @Param("userId") UUID userId);

    @Query("SELECT COUNT(p) > 0 FROM Participant p WHERE p.id.conversationId = :convId AND p.id.userId = :userId AND p.active = true")
    boolean isActiveParticipant(@Param("convId") UUID conversationId, @Param("userId") UUID userId);
}
