package arsw.asclepio.talk.dto.request;

import jakarta.validation.constraints.Size;

// Daniel Useche
public record UpdateConversationRequest(

        @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
        String name,

        String description
) {}
