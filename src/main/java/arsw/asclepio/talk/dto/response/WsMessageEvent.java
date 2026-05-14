package arsw.asclepio.talk.dto.response;

import java.util.UUID;

// Payload enviado por WebSocket a todos los suscriptores de una conversación
// Daniel Useche
public record WsMessageEvent(
        String type,        // MESSAGE | MESSAGE_UPDATED | MESSAGE_DELETED | TYPING
        UUID conversationId,
        MessageResponse message
) {
    public static WsMessageEvent newMessage(UUID convId, MessageResponse msg) {
        return new WsMessageEvent("MESSAGE", convId, msg);
    }

    public static WsMessageEvent updatedMessage(UUID convId, MessageResponse msg) {
        return new WsMessageEvent("MESSAGE_UPDATED", convId, msg);
    }

    public static WsMessageEvent deletedMessage(UUID convId, MessageResponse msg) {
        return new WsMessageEvent("MESSAGE_DELETED", convId, msg);
    }
}
