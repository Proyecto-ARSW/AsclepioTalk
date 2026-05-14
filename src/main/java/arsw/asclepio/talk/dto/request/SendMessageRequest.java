package arsw.asclepio.talk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Daniel Useche
public record SendMessageRequest(

        @NotBlank(message = "El contenido del mensaje no puede estar vacío")
        @Size(max = 4000, message = "El mensaje no puede superar 4000 caracteres")
        String content
) {}
