package arsw.asclepio.talk.exception;

// Daniel Useche
public class ForbiddenActionException extends RuntimeException {
    public ForbiddenActionException(String action) {
        super("Acción no permitida: " + action);
    }
}
