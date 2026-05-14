package arsw.asclepio.talk.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Daniel Useche
public record AddParticipantRequest(

        @NotNull(message = "El userId es obligatorio")
        UUID userId,

        @NotNull(message = "El nombre del participante es obligatorio")
        String userName,

        @NotNull(message = "El rol del participante es obligatorio")
        String userRol
) {}
