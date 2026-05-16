package arsw.asclepio.talk.domain.message;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// Reacción individual: (mensaje, usuario, emoji). La unicidad en BD
// garantiza que el mismo usuario no añade el mismo emoji dos veces al
// mismo mensaje — el servicio captura la violación y la convierte en idempotencia.
@Entity
@Table(name = "message_reactions", schema = "talk",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reactions_message_user_emoji",
                columnNames = {"message_id", "user_id", "emoji"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Daniel Useche
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Guardamos el nombre del usuario para evitar joins futuros con la tabla
    // de usuarios (que vive en otro microservicio). Snapshot al momento de reaccionar.
    @Column(name = "user_name", nullable = false, length = 200)
    private String userName;

    @Column(name = "emoji", nullable = false, length = 16)
    private String emoji;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
