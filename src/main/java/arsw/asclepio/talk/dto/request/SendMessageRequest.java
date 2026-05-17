package arsw.asclepio.talk.dto.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

// El `content` puede venir vacío cuando el mensaje lleva adjunto. La regla
// "al menos texto o archivo" se valida en MessageService.send, donde tenemos
// el contexto completo (la presencia del MultipartFile).
//
// Daniel Useche
public record SendMessageRequest(

        @Size(max = 4000, message = "El mensaje no puede superar 4000 caracteres")
        String content,

        // Opcional: si se incluye, este mensaje es una respuesta al referenciado.
        // El servicio valida que el padre exista y pertenezca a la misma conversación.
        UUID replyToMessageId
) {}
