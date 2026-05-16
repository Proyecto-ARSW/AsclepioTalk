package arsw.asclepio.talk.dto.response;

import arsw.asclepio.talk.domain.message.Message;
import arsw.asclepio.talk.security.UserPrincipal;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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
        LocalDateTime createdAt,
        // Campos UX nuevos. Siempre serializados (incluso vacíos / null)
        // para que el cliente nunca tenga que defenderse de `undefined`.
        ReplyPreviewResponse replyTo,
        boolean pinned,
        UUID pinnedBy,
        LocalDateTime pinnedAt,
        List<ReactionGroupResponse> reactions,
        List<ReadReceiptResponse> readBy
) {
    // Factory minimal — usado cuando aún no tenemos info de reactions/reads
    // (por ejemplo en el push WS de "MESSAGE" recién creado, donde aún no
    // hay reacciones ni lecturas registradas).
    public static MessageResponse from(Message m, UserPrincipal viewer) {
        return from(m, viewer, null, Collections.emptyList(), Collections.emptyList());
    }

    // Factory enriquecido — el caller pasa los datos pre-cargados en batch
    // para evitar N+1. replyParent puede ser null si el mensaje no responde a nada.
    public static MessageResponse from(Message m,
                                       UserPrincipal viewer,
                                       Message replyParent,
                                       List<ReactionGroupResponse> reactions,
                                       List<ReadReceiptResponse> readBy) {
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
                m.getCreatedAt(),
                ReplyPreviewResponse.from(replyParent),
                m.isPinned(),
                m.getPinnedBy(),
                m.getPinnedAt(),
                reactions == null ? Collections.emptyList() : reactions,
                readBy == null ? Collections.emptyList() : readBy
        );
    }
}
