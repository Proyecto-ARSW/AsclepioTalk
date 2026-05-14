package arsw.asclepio.talk.controller;

import arsw.asclepio.talk.dto.request.SendMessageRequest;
import arsw.asclepio.talk.dto.response.WsTypingEvent;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.MessageService;
import arsw.asclepio.talk.service.WebSocketNotifier;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.UUID;

// Daniel Useche
@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final MessageService messageService;
    private final WebSocketNotifier notifier;

    // Cliente publica en /app/conversation/{id}.send
    @MessageMapping("/conversation/{id}.send")
    public void sendMessage(@DestinationVariable UUID id,
                            @Valid @Payload SendMessageRequest req,
                            Authentication auth) {
        UserPrincipal user = (UserPrincipal) auth.getPrincipal();
        messageService.send(id, req, user);
    }

    // Cliente publica en /app/conversation/{id}.typing
    @MessageMapping("/conversation/{id}.typing")
    public void typing(@DestinationVariable UUID id,
                       @Payload boolean isTyping,
                       Authentication auth) {
        UserPrincipal user = (UserPrincipal) auth.getPrincipal();
        notifier.broadcastTyping(id, new WsTypingEvent(
                "TYPING", id, user.userId(), user.fullName(), isTyping
        ));
    }
}
