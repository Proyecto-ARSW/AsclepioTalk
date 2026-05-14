package arsw.asclepio.talk.exception;

import java.util.UUID;

// Daniel Useche
public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(UUID id) {
        super("Conversación no encontrada: " + id);
    }
}
