package arsw.asclepio.talk.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
// Daniel Useche
public interface MessageReadRepository extends JpaRepository<MessageRead, MessageReadId> {

    // Carga en lote los receipts de un set de mensajes — evita N+1 al
    // armar el listado de mensajes con sus readBy.
    @Query("SELECT r FROM MessageRead r WHERE r.id.messageId IN :messageIds")
    List<MessageRead> findByMessageIds(@Param("messageIds") Collection<UUID> messageIds);

    // Insert idempotente: si el par (message_id, user_id) ya existe,
    // no hace nada. Postgres soporta ON CONFLICT DO NOTHING en nativo;
    // no usamos JPA insert porque exigiría try/catch por cada fila.
    @Modifying
    @Query(value = """
            INSERT INTO talk.message_reads (message_id, user_id, read_at)
            VALUES (:messageId, :userId, now())
            ON CONFLICT (message_id, user_id) DO NOTHING
            """, nativeQuery = true)
    void upsertRead(@Param("messageId") UUID messageId, @Param("userId") UUID userId);
}
