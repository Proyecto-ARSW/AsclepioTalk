package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.conversation.ParticipantRepository;
import arsw.asclepio.talk.domain.message.Message;
import arsw.asclepio.talk.domain.message.MessageRepository;
import arsw.asclepio.talk.dto.request.EditMessageRequest;
import arsw.asclepio.talk.dto.request.SendMessageRequest;
import arsw.asclepio.talk.dto.response.MessageResponse;
import arsw.asclepio.talk.dto.response.WsMessageEvent;
import arsw.asclepio.talk.exception.ForbiddenActionException;
import arsw.asclepio.talk.exception.MessageNotFoundException;
import arsw.asclepio.talk.exception.NotParticipantException;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.CensorshipService.CensorResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

// Daniel Useche
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepo;
    private final ParticipantRepository participantRepo;
    private final CensorshipService censorshipService;
    private final WebSocketNotifier notifier;

    @Transactional
    public MessageResponse send(UUID conversationId, SendMessageRequest req, UserPrincipal sender) {
        requireParticipant(conversationId, sender.userId());

        CensorResult censored = censorshipService.censor(req.content());

        Message msg = messageRepo.save(Message.builder()
                .conversationId(conversationId)
                .senderId(sender.userId())
                .senderName(sender.fullName())
                .contentOriginal(req.content())
                .contentDisplay(censored.displayContent())
                .autoCensored(censored.wasCensored())
                .build());

        MessageResponse response = MessageResponse.from(msg, sender);
        notifier.broadcastMessage(conversationId, WsMessageEvent.newMessage(conversationId, response));
        return response;
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> list(UUID conversationId, UserPrincipal user, Pageable pageable) {
        requireParticipant(conversationId, user.userId());
        return messageRepo.findVisibleByConversation(conversationId, pageable)
                .map(m -> MessageResponse.from(m, user));
    }

    @Transactional
    public MessageResponse edit(UUID conversationId, UUID messageId, EditMessageRequest req, UserPrincipal user) {
        Message msg = findVisible(messageId);
        requireParticipant(conversationId, user.userId());

        // Solo el remitente puede editar su propio mensaje
        if (!msg.getSenderId().equals(user.userId())) {
            throw new ForbiddenActionException("Solo el autor puede editar su mensaje");
        }

        CensorResult censored = censorshipService.censor(req.content());
        msg.setContentOriginal(req.content());
        msg.setContentDisplay(censored.displayContent());
        msg.setAutoCensored(censored.wasCensored());
        msg.setEdited(true);
        msg.setEditedAt(LocalDateTime.now());

        Message saved = messageRepo.save(msg);
        MessageResponse response = MessageResponse.from(saved, user);
        notifier.broadcastMessage(conversationId, WsMessageEvent.updatedMessage(conversationId, response));
        return response;
    }

    @Transactional
    public void delete(UUID conversationId, UUID messageId, UserPrincipal user) {
        Message msg = findVisible(messageId);
        requireParticipant(conversationId, user.userId());

        boolean isSender = msg.getSenderId().equals(user.userId());
        boolean canForceDelete = user.isAdmin() || user.isMedico();

        if (!isSender && !canForceDelete) {
            throw new ForbiddenActionException("Sin permiso para eliminar este mensaje");
        }

        msg.setDeleted(true);
        msg.setDeletedBy(user.userId());
        msg.setDeletedAt(LocalDateTime.now());
        msg.setContentDisplay("[Mensaje eliminado]");

        Message saved = messageRepo.save(msg);
        MessageResponse response = MessageResponse.from(saved, user);
        notifier.broadcastMessage(conversationId, WsMessageEvent.deletedMessage(conversationId, response));
    }

    @Transactional
    public MessageResponse censorManually(UUID conversationId, UUID messageId, UserPrincipal user) {
        if (!user.canCensorMessage()) {
            throw new ForbiddenActionException("Solo MEDICO y ADMIN pueden censurar mensajes");
        }

        Message msg = findVisible(messageId);
        requireParticipant(conversationId, user.userId());

        msg.setManuallyCensored(true);
        msg.setCensoredBy(user.userId());
        msg.setCensoredByName(user.fullName());
        msg.setCensoredAt(LocalDateTime.now());
        // Contenido visible reemplazado por texto estándar según lo acordado
        msg.setContentDisplay("[Mensaje censurado por " + user.fullName() + "]");

        Message saved = messageRepo.save(msg);
        log.info("Mensaje {} censurado manualmente por {}", messageId, user.fullName());

        MessageResponse response = MessageResponse.from(saved, user);
        notifier.broadcastMessage(conversationId, WsMessageEvent.updatedMessage(conversationId, response));
        return response;
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private Message findVisible(UUID messageId) {
        return messageRepo.findById(messageId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new MessageNotFoundException(messageId));
    }

    private void requireParticipant(UUID convId, UUID userId) {
        if (!participantRepo.isActiveParticipant(convId, userId)) {
            throw new NotParticipantException(userId, convId);
        }
    }
}
