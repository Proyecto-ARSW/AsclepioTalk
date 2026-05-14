package arsw.asclepio.talk.domain.conversation;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_participants", schema = "talk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Daniel Useche
public class Participant {

    @EmbeddedId
    private ParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(name = "user_name", nullable = false, length = 200)
    private String userName;

    @Column(name = "user_rol", nullable = false, length = 30)
    private String userRol;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
