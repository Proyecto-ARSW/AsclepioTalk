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
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    // Todas las conversaciones activas de un hospital donde el usuario participa
    @Query("""
        SELECT DISTINCT c FROM Conversation c
        JOIN c.participants p
        WHERE c.hospitalId = :hospitalId
          AND c.active = true
          AND p.id.userId = :userId
          AND p.active = true
        ORDER BY c.updatedAt DESC
        """)
    List<Conversation> findActiveByHospitalAndUser(
            @Param("hospitalId") Integer hospitalId,
            @Param("userId") UUID userId);

    // Busca chat INDIVIDUAL existente entre dos usuarios en el mismo hospital
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'INDIVIDUAL'
          AND c.hospitalId = :hospitalId
          AND c.active = true
          AND EXISTS (
              SELECT p1 FROM Participant p1
              WHERE p1.conversation = c AND p1.id.userId = :userA AND p1.active = true
          )
          AND EXISTS (
              SELECT p2 FROM Participant p2
              WHERE p2.conversation = c AND p2.id.userId = :userB AND p2.active = true
          )
        """)
    Optional<Conversation> findIndividualBetween(
            @Param("hospitalId") Integer hospitalId,
            @Param("userA") UUID userA,
            @Param("userB") UUID userB);
}
