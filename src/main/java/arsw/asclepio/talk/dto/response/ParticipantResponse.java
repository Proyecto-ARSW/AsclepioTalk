package arsw.asclepio.talk.dto.response;

import arsw.asclepio.talk.domain.conversation.Participant;

import java.time.LocalDateTime;
import java.util.UUID;

// Daniel Useche
public record ParticipantResponse(
        UUID userId,
        String userName,
        String userRol,
        LocalDateTime joinedAt
) {
    public static ParticipantResponse from(Participant p) {
        return new ParticipantResponse(
                p.getId().getUserId(),
                p.getUserName(),
                p.getUserRol(),
                p.getJoinedAt()
        );
    }

    public static ParticipantResponse from(Participant p, boolean masked) {
        if (masked) {
            return new ParticipantResponse(
                    p.getId().getUserId(),
                    "Anónimo",
                    null,
                    p.getJoinedAt()
            );
        }
        return from(p);
    }
}
