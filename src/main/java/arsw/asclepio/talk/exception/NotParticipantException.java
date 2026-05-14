package arsw.asclepio.talk.exception;

import java.util.UUID;

// Daniel Useche
public class NotParticipantException extends RuntimeException {
    public NotParticipantException(UUID userId, UUID conversationId) {
        super("El usuario " + userId + " no es participante de la conversación " + conversationId);
    }
}
