package arsw.asclepio.talk.domain.message;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages", schema = "talk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Daniel Useche
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "sender_name", nullable = false, length = 200)
    private String senderName;

    // Contenido original guardado para auditoría — solo visible para ADMIN
    @Column(name = "content_original", nullable = false, columnDefinition = "TEXT")
    private String contentOriginal;

    // Contenido mostrado a los usuarios (puede tener censura aplicada)
    @Column(name = "content_display", nullable = false, columnDefinition = "TEXT")
    private String contentDisplay;

    @Column(name = "auto_censored", nullable = false)
    @Builder.Default
    private boolean autoCensored = false;

    @Column(name = "manually_censored", nullable = false)
    @Builder.Default
    private boolean manuallyCensored = false;

    @Column(name = "censored_by")
    private UUID censoredBy;

    @Column(name = "censored_by_name", length = 200)
    private String censoredByName;

    @Column(name = "censored_at")
    private LocalDateTime censoredAt;

    @Column(name = "edited", nullable = false)
    @Builder.Default
    private boolean edited = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
