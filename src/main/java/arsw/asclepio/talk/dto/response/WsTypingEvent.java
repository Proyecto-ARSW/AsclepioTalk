package arsw.asclepio.talk.dto.response;

import java.util.UUID;

// Evento de indicador de escritura — no se persiste
// Daniel Useche
public record WsTypingEvent(
        String type,   // siempre "TYPING"
        UUID conversationId,
        UUID userId,
        String userName,
        boolean typing
) {}
