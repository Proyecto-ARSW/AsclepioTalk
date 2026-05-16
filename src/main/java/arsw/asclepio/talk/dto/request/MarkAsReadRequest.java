package arsw.asclepio.talk.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Daniel Useche
public record MarkAsReadRequest(

        // Batch: el cliente envía un set de mensajes que acaba de "ver" en pantalla.
        // El tope evita que un cliente malicioso mande 50k IDs en una sola request.
        @NotEmpty(message = "Debe incluir al menos un mensaje")
        @Size(max = 200, message = "No se pueden marcar más de 200 mensajes a la vez")
        List<UUID> messageIds
) {}
