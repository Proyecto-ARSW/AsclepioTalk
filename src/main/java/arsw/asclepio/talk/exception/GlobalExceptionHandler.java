package arsw.asclepio.talk.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

// Daniel Useche
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    public ProblemDetail handleConversationNotFound(ConversationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "conversation-not-found", ex.getMessage());
    }

    @ExceptionHandler(MessageNotFoundException.class)
    public ProblemDetail handleMessageNotFound(MessageNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "message-not-found", ex.getMessage());
    }

    @ExceptionHandler(NotParticipantException.class)
    public ProblemDetail handleNotParticipant(NotParticipantException ex) {
        return problem(HttpStatus.FORBIDDEN, "not-participant", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenActionException.class)
    public ProblemDetail handleForbidden(ForbiddenActionException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden-action", ex.getMessage());
    }

    @ExceptionHandler(DuplicateWordException.class)
    public ProblemDetail handleDuplicate(DuplicateWordException ex) {
        return problem(HttpStatus.CONFLICT, "duplicate-word", ex.getMessage());
    }

    // Las validaciones del AttachmentStorageService (MIME, tamano) y otras
    // precondiciones de negocio se traducen como 400 con el mensaje del
    // service — el frontend lo muestra al usuario.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        // WARN sin stacktrace: si es un 400 legitimo (validacion de negocio)
        // solo queremos saber que ocurrio; si es un bug interno camuflado,
        // el mensaje suele bastar para localizarlo. Subir a ERROR meteria
        // ruido por cada request invalido del frontend.
        log.warn("Argumento invalido: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "invalid-argument", ex.getMessage());
    }

    // Spring rechaza el request antes de que llegue al controller cuando el
    // archivo supera spring.servlet.multipart.max-file-size. Lo mapeamos a
    // 413 con mensaje claro para que el frontend muestre el mismo error que
    // ya valido en cliente.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        // WARN: el rechazo es esperado (limite del servlet), no necesitamos
        // stacktrace. Solo dejamos rastro para detectar abusos o clientes
        // que omiten la validacion local.
        log.warn("Upload rechazado por tamano: {}", ex.getMessage());
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "file-too-large",
                "El archivo supera el limite permitido");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "inválido"
                ));
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "validation-error", "Error de validación");
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        // CRITICO: pasar `ex` como ultimo argumento del varargs hace que
        // SLF4J imprima el stacktrace completo. Si solo pasamos ex.getMessage()
        // perdemos la cadena de causas y nos quedamos ciegos en despliegue,
        // que es justo el escenario que dispara este plan.
        log.error("Excepcion no manejada en {} -> {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Error interno del servidor");
    }

    private ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://asclepio.talk/errors/" + type));
        return pd;
    }
}
