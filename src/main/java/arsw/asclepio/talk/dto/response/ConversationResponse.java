package arsw.asclepio.talk.dto.response;

import arsw.asclepio.talk.domain.conversation.Conversation;
import arsw.asclepio.talk.domain.conversation.ConversationType;
import arsw.asclepio.talk.domain.conversation.Participant;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Daniel Useche
public record ConversationResponse(
        UUID id,
        ConversationType type,
        String name,
        String description,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean anonymous,
        List<ParticipantResponse> participants
) {
    public static ConversationResponse from(Conversation c) {
        return new ConversationResponse(
                c.getId(),
                c.getType(),
                c.getName(),
                c.getDescription(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.isAnonymous(),
                c.getParticipants().stream()
                        .filter(p -> p.isActive())
                        .map(ParticipantResponse::from)
                        .toList()
        );
    }

    public static ConversationResponse from(Conversation c, UUID viewerId) {
        return fromWithParticipants(c, c.getParticipants(), viewerId);
    }

    // Variante que acepta la lista de Participant explícitamente, en lugar de
    // leerla de c.getParticipants(). La usamos justo después de crear una
    // conversación, cuando la colección @OneToMany de la entidad managed aún
    // no refleja los participants recién insertados — sin esto, el frontend
    // recibía un grupo "vacío" hasta el primer reload o update.
    public static ConversationResponse fromWithParticipants(
            Conversation c,
            List<Participant> participants,
            UUID viewerId
    ) {
        boolean isCreator = c.getCreatedBy().equals(viewerId);
        boolean shouldMask = c.isAnonymous() && !isCreator;

        return new ConversationResponse(
                c.getId(),
                c.getType(),
                c.getName(),
                c.getDescription(),
                c.getCreatedBy(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.isAnonymous(),
                participants.stream()
                        .filter(Participant::isActive)
                        .map(p -> {
                            boolean isSelf = p.getId().getUserId().equals(viewerId);
                            boolean masked = shouldMask && !isSelf;
                            return ParticipantResponse.from(p, masked);
                        })
                        .toList()
        );
    }
}
