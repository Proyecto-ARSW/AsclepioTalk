package arsw.asclepio.talk.service;

import arsw.asclepio.talk.dto.response.ConversationResponse;
import arsw.asclepio.talk.dto.response.WsMessageEvent;
import arsw.asclepio.talk.dto.response.WsTypingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Centraliza el envío de eventos por WebSocket/STOMP
// Daniel Useche
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastMessage(UUID conversationId, WsMessageEvent event) {
        String destination = "/topic/conversation." + conversationId;
        messagingTemplate.convertAndSend(destination, event);
    }

    public void broadcastTyping(UUID conversationId, WsTypingEvent event) {
        String destination = "/topic/conversation." + conversationId;
        messagingTemplate.convertAndSend(destination, event);
    }

    // Notificación personal — nueva conversación creada, expulsión de grupo, etc.
    public void notifyUser(String userId, Object payload) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload);
    }

    public void notifyNewConversation(UUID targetUserId, ConversationResponse conversation) {
        notifyUser(targetUserId.toString(), conversation);
    }
}
