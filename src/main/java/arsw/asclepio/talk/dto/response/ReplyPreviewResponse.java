package arsw.asclepio.talk.dto.response;

import java.util.UUID;

// Snippet del mensaje al que se está respondiendo. Llevamos solo lo necesario
// para renderizar la cita (avatar + nombre + 80 chars del contenido), no la
// entidad completa — evita serializar payloads innecesariamente grandes.
// Daniel Useche
public record ReplyPreviewResponse(
        UUID messageId,
        UUID senderId,
        String senderName,
        String snippet,
        boolean deleted
) {
    private static final int SNIPPET_MAX_LEN = 80;

    public static ReplyPreviewResponse from(arsw.asclepio.talk.domain.message.Message parent) {
        if (parent == null) {
            return null;
        }
        String content = parent.isDeleted() ? "" : parent.getContentDisplay();
        String snippet = content == null ? "" :
                (content.length() <= SNIPPET_MAX_LEN
                        ? content
                        : content.substring(0, SNIPPET_MAX_LEN) + "…");
        return new ReplyPreviewResponse(
                parent.getId(),
                parent.getSenderId(),
                parent.getSenderName(),
                snippet,
                parent.isDeleted()
        );
    }
}
