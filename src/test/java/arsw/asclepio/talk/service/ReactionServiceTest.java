package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.conversation.ParticipantRepository;
import arsw.asclepio.talk.domain.message.Message;
import arsw.asclepio.talk.domain.message.MessageReactionRepository;
import arsw.asclepio.talk.domain.message.MessageRepository;
import arsw.asclepio.talk.exception.MessageNotFoundException;
import arsw.asclepio.talk.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Daniel Useche
@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock MessageReactionRepository reactionRepo;
    @Mock MessageRepository messageRepo;
    @Mock ParticipantRepository participantRepo;
    @Mock WebSocketNotifier notifier;

    @InjectMocks ReactionService service;

    private final UUID convId = UUID.randomUUID();
    private final UserPrincipal user = new UserPrincipal(
            UUID.randomUUID(), "u@hosp.com", "PACIENTE", "Ana", "Lopez", 1
    );

    private Message makeMessage(UUID msgId, UUID conversationId) {
        return Message.builder()
                .id(msgId)
                .conversationId(conversationId)
                .senderId(UUID.randomUUID())
                .senderName("X")
                .contentOriginal("hola")
                .contentDisplay("hola")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Reacción nueva se persiste y emite REACTION_ADDED")
    void addNewReactionPersistsAndBroadcasts() {
        UUID msgId = UUID.randomUUID();
        when(messageRepo.findById(msgId)).thenReturn(Optional.of(makeMessage(msgId, convId)));
        when(participantRepo.isActiveParticipant(convId, user.userId())).thenReturn(true);

        service.add(convId, msgId, "👍", user);

        verify(reactionRepo).save(any());
        verify(notifier).broadcastMessage(eq(convId), any());
    }

    @Test
    @DisplayName("Reacción duplicada (DataIntegrityViolation) es tratada como idempotente")
    void duplicateReactionIsIdempotent() {
        UUID msgId = UUID.randomUUID();
        when(messageRepo.findById(msgId)).thenReturn(Optional.of(makeMessage(msgId, convId)));
        when(participantRepo.isActiveParticipant(convId, user.userId())).thenReturn(true);
        when(reactionRepo.save(any())).thenThrow(new DataIntegrityViolationException("uq violated"));

        // No debe lanzar — el servicio captura la violación.
        service.add(convId, msgId, "👍", user);

        // No se emite WS event si ya existía (evita re-broadcasts redundantes).
        verify(notifier, never()).broadcastMessage(any(), any());
    }

    @Test
    @DisplayName("Remove inexistente no falla y no emite evento")
    void removeNonexistentIsNoop() {
        UUID msgId = UUID.randomUUID();
        when(messageRepo.findById(msgId)).thenReturn(Optional.of(makeMessage(msgId, convId)));
        when(participantRepo.isActiveParticipant(convId, user.userId())).thenReturn(true);
        when(reactionRepo.deleteByMessageIdAndUserIdAndEmoji(msgId, user.userId(), "👍")).thenReturn(0L);

        service.remove(convId, msgId, "👍", user);

        verify(notifier, never()).broadcastMessage(any(), any());
    }

    @Test
    @DisplayName("Remove existente emite REACTION_REMOVED")
    void removeExistingBroadcasts() {
        UUID msgId = UUID.randomUUID();
        when(messageRepo.findById(msgId)).thenReturn(Optional.of(makeMessage(msgId, convId)));
        when(participantRepo.isActiveParticipant(convId, user.userId())).thenReturn(true);
        when(reactionRepo.deleteByMessageIdAndUserIdAndEmoji(msgId, user.userId(), "👍")).thenReturn(1L);

        service.remove(convId, msgId, "👍", user);

        verify(notifier).broadcastMessage(eq(convId), any());
    }

    @Test
    @DisplayName("Reacción a mensaje de otra conversación es rechazada")
    void reactToForeignConversationIsRejected() {
        UUID msgId = UUID.randomUUID();
        // Mensaje pertenece a OTRA conversación
        when(messageRepo.findById(msgId)).thenReturn(Optional.of(makeMessage(msgId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.add(convId, msgId, "👍", user))
                .isInstanceOf(MessageNotFoundException.class);
    }
}
