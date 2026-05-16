package arsw.asclepio.talk.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Payload enviado por WebSocket a todos los suscriptores de una conversación.
// type discrimina la variante; los campos opcionales se llenan según el tipo
// (Jackson serializa los null como ausentes gracias a default-property-inclusion: non_null).
// Daniel Useche
public record WsMessageEvent(
        String type,
        UUID conversationId,
        MessageResponse message,
        // Read receipt event payload
        UUID messageId,
        UUID userId,
        String userName,
        LocalDateTime timestamp,
        List<UUID> messageIds,
        // Reaction event payload
        String emoji,
        // Pin event payload — se reutiliza messageId y userName (quien lo fijó)
        Boolean pinned
) {
    // Mensajes nuevos / editados / eliminados — mantienen contrato anterior.
    public static WsMessageEvent newMessage(UUID convId, MessageResponse msg) {
        return new WsMessageEvent("MESSAGE", convId, msg, null, null, null, null, null, null, null);
    }

    public static WsMessageEvent updatedMessage(UUID convId, MessageResponse msg) {
        return new WsMessageEvent("MESSAGE_UPDATED", convId, msg, null, null, null, null, null, null, null);
    }

    public static WsMessageEvent deletedMessage(UUID convId, MessageResponse msg) {
        return new WsMessageEvent("MESSAGE_DELETED", convId, msg, null, null, null, null, null, null, null);
    }

    // Read receipt: el cliente que vio un set de mensajes notifica al resto.
    // Mandamos los messageIds en bloque (la UI los pinta como ✓✓ a la vez).
    public static WsMessageEvent messagesRead(UUID convId, UUID userId, String userName, List<UUID> messageIds) {
        return new WsMessageEvent(
                "MESSAGE_READ", convId, null,
                null, userId, userName, LocalDateTime.now(), messageIds,
                null, null
        );
    }

    public static WsMessageEvent reactionAdded(UUID convId, UUID messageId, UUID userId, String userName, String emoji) {
        return new WsMessageEvent(
                "REACTION_ADDED", convId, null,
                messageId, userId, userName, LocalDateTime.now(), null,
                emoji, null
        );
    }

    public static WsMessageEvent reactionRemoved(UUID convId, UUID messageId, UUID userId, String emoji) {
        return new WsMessageEvent(
                "REACTION_REMOVED", convId, null,
                messageId, userId, null, LocalDateTime.now(), null,
                emoji, null
        );
    }

    public static WsMessageEvent messagePinned(UUID convId, UUID messageId, UUID userId, String userName, boolean pinned) {
        return new WsMessageEvent(
                pinned ? "MESSAGE_PINNED" : "MESSAGE_UNPINNED", convId, null,
                messageId, userId, userName, LocalDateTime.now(), null,
                null, pinned
        );
    }
}
