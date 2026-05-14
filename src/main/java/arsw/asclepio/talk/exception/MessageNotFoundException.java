package arsw.asclepio.talk.exception;

import java.util.UUID;

// Daniel Useche
public class MessageNotFoundException extends RuntimeException {
    public MessageNotFoundException(UUID id) {
        super("Mensaje no encontrado: " + id);
    }
}
