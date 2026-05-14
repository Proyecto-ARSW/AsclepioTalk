package arsw.asclepio.talk.exception;

// Daniel Useche
public class DuplicateWordException extends RuntimeException {
    public DuplicateWordException(String word) {
        super("La palabra ya existe en la lista de censura: " + word);
    }
}
