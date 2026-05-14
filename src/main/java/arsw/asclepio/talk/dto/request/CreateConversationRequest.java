package arsw.asclepio.talk.dto.request;

import arsw.asclepio.talk.domain.conversation.ConversationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Daniel Useche
public record CreateConversationRequest(

        @NotNull(message = "El tipo de conversación es obligatorio")
        ConversationType type,

        @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
        String name,

        String description,

        @NotNull(message = "Debe incluir al menos un participante")
        @Size(min = 1, max = 50, message = "Entre 1 y 50 participantes")
        List<ParticipantInput> participants,

        boolean anonymous

) {
    public record ParticipantInput(
            @NotNull UUID userId,
            @NotBlank @Size(max = 200, message = "El nombre no puede superar 200 caracteres") String userName,
            @NotBlank @Size(max = 30, message = "El rol no puede superar 30 caracteres") String userRol
    ) {}
}
