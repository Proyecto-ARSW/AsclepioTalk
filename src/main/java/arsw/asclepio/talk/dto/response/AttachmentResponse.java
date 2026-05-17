package arsw.asclepio.talk.dto.response;

import arsw.asclepio.talk.domain.message.MessageAttachment;

import java.util.UUID;

// DTO que viaja al cliente con la info necesaria para renderizar el adjunto.
// `downloadUrl` se firma fresco en cada serializacion (TTL 10 min). No
// exponemos `storageKey` — el browser solo necesita la URL.
// Daniel Useche
public record AttachmentResponse(
        UUID id,
        String fileName,
        String mimeType,
        long sizeBytes,
        String downloadUrl,
        boolean isImage
) {
    public static AttachmentResponse from(MessageAttachment a, String downloadUrl) {
        if (a == null) {
            return null;
        }
        boolean image = a.getMimeType() != null && a.getMimeType().startsWith("image/");
        return new AttachmentResponse(
                a.getId(),
                a.getFileName(),
                a.getMimeType(),
                a.getSizeBytes(),
                downloadUrl,
                image
        );
    }
}
