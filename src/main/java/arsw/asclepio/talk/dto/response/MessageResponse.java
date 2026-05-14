package arsw.asclepio.talk.dto.response;

import arsw.asclepio.talk.domain.message.Message;
import arsw.asclepio.talk.security.UserPrincipal;

import java.time.LocalDateTime;
import java.util.UUID;

// Daniel Useche
public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderName,
        // contentDisplay es lo que se muestra. Solo ADMIN puede ver contentOriginal.
        String contentDisplay,
        String contentOriginal,
        boolean autoCensored,
        boolean manuallyCensored,
        String censoredByName,
        LocalDateTime censoredAt,
        boolean edited,
        LocalDateTime editedAt,
        boolean deleted,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message m, UserPrincipal viewer) {
        // Solo ADMIN puede ver el contenido original de un mensaje censurado
        String original = viewer != null && viewer.isAdmin() ? m.getContentOriginal() : null;
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                m.getSenderName(),
                m.getContentDisplay(),
                original,
                m.isAutoCensored(),
                m.isManuallyCensored(),
                m.getCensoredByName(),
                m.getCensoredAt(),
                m.isEdited(),
                m.getEditedAt(),
                m.isDeleted(),
                m.getCreatedAt()
        );
    }
}
