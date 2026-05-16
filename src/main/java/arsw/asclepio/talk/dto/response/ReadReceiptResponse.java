package arsw.asclepio.talk.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

// Una marca de lectura por usuario. userName lo resolvemos desde Participant
// (snapshot al momento de unirse a la conversación), evitando ir a M1.
// Daniel Useche
public record ReadReceiptResponse(
        UUID userId,
        String userName,
        LocalDateTime readAt
) {}
