package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.conversation.ParticipantRepository;
import arsw.asclepio.talk.domain.message.Message;
import arsw.asclepio.talk.domain.message.MessageReaction;
import arsw.asclepio.talk.domain.message.MessageReactionRepository;
import arsw.asclepio.talk.domain.message.MessageRepository;
import arsw.asclepio.talk.dto.response.WsMessageEvent;
import arsw.asclepio.talk.exception.MessageNotFoundException;
import arsw.asclepio.talk.exception.NotParticipantException;
import arsw.asclepio.talk.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Gestiona reacciones emoji sobre mensajes. Cualquier participante de la
// conversación puede reaccionar — sin distinción de rol; las expresiones
// emocionales son libres (a diferencia del pin/censura que sí son restringidos).
// Daniel Useche
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactionService {

    private final MessageReactionRepository reactionRepo;
    private final MessageRepository messageRepo;
    private final ParticipantRepository participantRepo;
    private final WebSocketNotifier notifier;

    @Transactional
    public void add(UUID conversationId, UUID messageId, String emoji, UserPrincipal user) {
        Message msg = findVisibleInConversation(conversationId, messageId);
        requireParticipant(conversationId, user.userId());

        // Idempotente por diseño: el UNIQUE de BD nos rescata si dos requests
        // concurrentes intentan insertar el mismo (mensaje, usuario, emoji).
        // Capturamos la violación y consideramos la operación exitosa.
        try {
            reactionRepo.save(MessageReaction.builder()
                    .messageId(msg.getId())
                    .userId(user.userId())
                    .userName(user.fullName())
                    .emoji(emoji)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.debug("Reaction {} on {} by {} ya existía — ignorado", emoji, messageId, user.userId());
            return;
        }

        notifier.broadcastMessage(conversationId,
                WsMessageEvent.reactionAdded(conversationId, messageId, user.userId(), user.fullName(), emoji));
    }

    @Transactional
    public void remove(UUID conversationId, UUID messageId, String emoji, UserPrincipal user) {
        // findVisibleInConversation también valida pertenencia del mensaje a la conv.
        findVisibleInConversation(conversationId, messageId);
        requireParticipant(conversationId, user.userId());

        long removed = reactionRepo.deleteByMessageIdAndUserIdAndEmoji(messageId, user.userId(), emoji);
        if (removed == 0) {
            // Nada que borrar — también es idempotente, no es un error.
            log.debug("Reaction {} on {} by {} no existía — no-op", emoji, messageId, user.userId());
            return;
        }

        notifier.broadcastMessage(conversationId,
                WsMessageEvent.reactionRemoved(conversationId, messageId, user.userId(), emoji));
    }

    private Message findVisibleInConversation(UUID conversationId, UUID messageId) {
        Message msg = messageRepo.findById(messageId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new MessageNotFoundException(messageId));
        // Validamos que el mensaje pertenece a la conversación que dice el path —
        // evita que un participante de la convo A reaccione a mensajes de la convo B.
        if (!msg.getConversationId().equals(conversationId)) {
            throw new MessageNotFoundException(messageId);
        }
        return msg;
    }

    private void requireParticipant(UUID convId, UUID userId) {
        if (!participantRepo.isActiveParticipant(convId, userId)) {
            throw new NotParticipantException(userId, convId);
        }
    }
}
