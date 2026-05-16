package arsw.asclepio.talk.domain.message;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Read receipt: una fila por (mensaje, usuario). La PK compuesta hace que
// dos inserciones del mismo par sean detectables por JPA y evita duplicados
// (insertamos con saveAndFlush() dentro de try/catch para idempotencia).
@Entity
@Table(name = "message_reads", schema = "talk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Daniel Useche
public class MessageRead {

    @EmbeddedId
    private MessageReadId id;

    @Column(name = "read_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime readAt = LocalDateTime.now();
}
