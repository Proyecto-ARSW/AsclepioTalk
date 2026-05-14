package arsw.asclepio.talk.dto.response;

import arsw.asclepio.talk.domain.conversation.Conversation;
import arsw.asclepio.talk.domain.conversation.ConversationType;

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
                c.getParticipants().stream()
                        .filter(p -> p.isActive())
                        .map(p -> {
                            boolean isSelf = p.getId().getUserId().equals(viewerId);
                            boolean masked = shouldMask && !isSelf;
                            return ParticipantResponse.from(p, masked);
                        })
                        .toList()
        );
    }
}
