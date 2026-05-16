package arsw.asclepio.talk.domain.message;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

// Clave primaria compuesta (message_id, user_id). Hereda Serializable porque
// JPA exige que los @EmbeddedId lo sean (la PK puede viajar entre capas).
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
// Daniel Useche
public class MessageReadId implements Serializable {

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "user_id")
    private UUID userId;
}
