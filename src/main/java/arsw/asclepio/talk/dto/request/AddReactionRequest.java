package arsw.asclepio.talk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Daniel Useche
public record AddReactionRequest(

        // El emoji llega como string (puede ser un grapheme multi-codepoint).
        // VARCHAR(16) en BD da margen para emojis ZWJ-secuencias compuestas.
        @NotBlank(message = "El emoji no puede estar vacío")
        @Size(max = 16, message = "El emoji excede el tamaño máximo permitido")
        String emoji
) {}
