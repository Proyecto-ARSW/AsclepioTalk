package arsw.asclepio.talk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Daniel Useche
public record AddCensoredWordRequest(

        @NotBlank(message = "La palabra no puede estar vacía")
        @Size(max = 100, message = "La palabra no puede superar 100 caracteres")
        String word
) {}
