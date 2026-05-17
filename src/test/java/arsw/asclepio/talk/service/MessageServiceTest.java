package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.conversation.ParticipantRepository;
import arsw.asclepio.talk.domain.message.MessageAttachmentRepository;
import arsw.asclepio.talk.domain.message.Message;
import arsw.asclepio.talk.domain.message.MessageReactionRepository;
import arsw.asclepio.talk.domain.message.MessageReadRepository;
import arsw.asclepio.talk.domain.message.MessageRepository;
import arsw.asclepio.talk.dto.request.SendMessageRequest;
import arsw.asclepio.talk.service.AttachmentStorageService;
import arsw.asclepio.talk.dto.response.MessageResponse;
import arsw.asclepio.talk.exception.ForbiddenActionException;
import arsw.asclepio.talk.exception.MessageNotFoundException;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.CensorshipService.CensorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

// Daniel Useche
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

        @Mock MessageRepository messageRepo;
        @Mock MessageReactionRepository reactionRepo;
        @Mock MessageReadRepository readRepo;
        @Mock MessageAttachmentRepository attachmentRepo;
        @Mock ParticipantRepository participantRepo;
        @Mock CensorshipService censorshipService;
        @Mock AttachmentStorageService storageService;
        @Mock WebSocketNotifier notifier;

    @InjectMocks MessageService service;

    private final UUID convId = UUID.randomUUID();

    private final UserPrincipal medico = new UserPrincipal(
            UUID.randomUUID(), "dr@hosp.com", "MEDICO", "Pedro", "Romero", 1
    );

    private final UserPrincipal paciente = new UserPrincipal(
            UUID.randomUUID(), "pac@hosp.com", "PACIENTE", "Juan", "Perez", 1
    );

    @Test
    @DisplayName("Mensaje con groserías recibe censura automática")
    void sendCensorsContent() {
        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(true);
        when(censorshipService.censor("qué mierda")).thenReturn(new CensorResult("qué *****", true));

        Message saved = Message.builder()
                .id(UUID.randomUUID())
                .conversationId(convId)
                .senderId(medico.userId())
                .senderName(medico.fullName())
                .contentOriginal("qué mierda")
                .contentDisplay("qué *****")
                .autoCensored(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(messageRepo.save(any())).thenReturn(saved);

        MessageResponse response = service.send(convId, new SendMessageRequest("qué mierda", null), medico);

        assertThat(response.autoCensored()).isTrue();
        assertThat(response.contentDisplay()).isEqualTo("qué *****");
    }

    @Test
    @DisplayName("No participante no puede enviar mensaje")
    void nonParticipantCannotSend() {
        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(false);

        assertThatThrownBy(() -> service.send(convId, new SendMessageRequest("hola", null), medico))
                .isInstanceOf(arsw.asclepio.talk.exception.NotParticipantException.class);
    }

    @Test
    @DisplayName("Censura manual establece texto estándar en contentDisplay")
    void manualCensorSetsStandardText() {
        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(true);

        UUID msgId = UUID.randomUUID();
        Message msg = Message.builder()
                .id(msgId)
                .conversationId(convId)
                .senderId(UUID.randomUUID())
                .senderName("Otro")
                .contentOriginal("texto ofensivo")
                .contentDisplay("texto ofensivo")
                .createdAt(LocalDateTime.now())
                .build();

        when(messageRepo.findById(msgId)).thenReturn(Optional.of(msg));
        when(messageRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reactionRepo.findByMessageIds(anyList())).thenReturn(Collections.emptyList());
        when(readRepo.findByMessageIds(anyList())).thenReturn(Collections.emptyList());

        MessageResponse response = service.censorManually(convId, msgId, medico);

        assertThat(response.manuallyCensored()).isTrue();
        assertThat(response.contentDisplay()).contains("[Mensaje censurado por");
        assertThat(response.contentDisplay()).contains("Pedro Romero");
    }

    @Test
    @DisplayName("Solo el autor puede editar su mensaje")
    void onlyAuthorCanEdit() {
        UserPrincipal otroMedico = new UserPrincipal(
                UUID.randomUUID(), "otro@hosp.com", "MEDICO", "Otro", "Dr", 1
        );

        UUID msgId = UUID.randomUUID();
        Message msg = Message.builder()
                .id(msgId)
                .conversationId(convId)
                .senderId(medico.userId())
                .senderName("Pedro Romero")
                .contentOriginal("texto original")
                .contentDisplay("texto original")
                .createdAt(LocalDateTime.now())
                .build();

        when(participantRepo.isActiveParticipant(convId, otroMedico.userId())).thenReturn(true);
        when(messageRepo.findById(msgId)).thenReturn(Optional.of(msg));

        assertThatThrownBy(() -> service.edit(
                convId, msgId,
                new arsw.asclepio.talk.dto.request.EditMessageRequest("nuevo texto"),
                otroMedico
        )).isInstanceOf(ForbiddenActionException.class);
    }

    // ─── Reply / quote ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Reply válido a mensaje de la misma conversación lo persiste")
    void replyToSameConversationSucceeds() {
        UUID parentId = UUID.randomUUID();
        Message parent = Message.builder()
                .id(parentId)
                .conversationId(convId)
                .senderId(UUID.randomUUID())
                .senderName("Original")
                .contentOriginal("mensaje original")
                .contentDisplay("mensaje original")
                .createdAt(LocalDateTime.now())
                .build();

        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(true);
        when(messageRepo.findById(parentId)).thenReturn(Optional.of(parent));
        when(censorshipService.censor("respuesta")).thenReturn(new CensorResult("respuesta", false));
        when(messageRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = service.send(convId, new SendMessageRequest("respuesta", parentId), medico);

        assertThat(response.replyTo()).isNotNull();
        assertThat(response.replyTo().messageId()).isEqualTo(parentId);
        assertThat(response.replyTo().snippet()).isEqualTo("mensaje original");
    }

    @Test
    @DisplayName("Reply a mensaje de otra conversación es rechazado")
    void replyToOtherConversationIsRejected() {
        UUID parentId = UUID.randomUUID();
        Message parent = Message.builder()
                .id(parentId)
                .conversationId(UUID.randomUUID()) // ← OTRA conversación
                .senderId(UUID.randomUUID())
                .senderName("Original")
                .contentOriginal("hola")
                .contentDisplay("hola")
                .createdAt(LocalDateTime.now())
                .build();

        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(true);
        when(messageRepo.findById(parentId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.send(convId,
                new SendMessageRequest("intento", parentId), medico))
                .isInstanceOf(MessageNotFoundException.class);
    }

    // ─── Pin / Unpin ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("PACIENTE no puede fijar mensajes")
    void pacienteCannotPin() {
        // togglePin verifica rol PRIMERO — ni siquiera llega a consultar participante.
        assertThatThrownBy(() -> service.togglePin(convId, UUID.randomUUID(), paciente))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    @DisplayName("MEDICO toggle pin activa pinned=true y emite evento")
    void medicoTogglePinActivates() {
        UUID msgId = UUID.randomUUID();
        Message msg = Message.builder()
                .id(msgId)
                .conversationId(convId)
                .senderId(UUID.randomUUID())
                .senderName("X")
                .contentOriginal("nota clínica")
                .contentDisplay("nota clínica")
                .pinned(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(true);
        when(messageRepo.findById(msgId)).thenReturn(Optional.of(msg));
        when(messageRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reactionRepo.findByMessageIds(anyList())).thenReturn(Collections.emptyList());
        when(readRepo.findByMessageIds(anyList())).thenReturn(Collections.emptyList());

        MessageResponse response = service.togglePin(convId, msgId, medico);

        assertThat(response.pinned()).isTrue();
        assertThat(response.pinnedBy()).isEqualTo(medico.userId());
        verify(notifier).broadcastMessage(eq(convId), any());
    }

    // ─── Read receipts ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("markAsRead solo procesa mensajes de la conversación correcta")
    void markAsReadFiltersForeignMessages() {
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        Message own = Message.builder().id(m1).conversationId(convId).build();
        Message foreign = Message.builder().id(m2).conversationId(UUID.randomUUID()).build();

        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(true);
        when(messageRepo.findByIds(List.of(m1, m2))).thenReturn(List.of(own, foreign));

        service.markAsRead(convId, List.of(m1, m2), medico);

        // Solo el mensaje propio de la conv debe ser insertado.
        verify(readRepo, times(1)).upsertRead(m1, medico.userId());
        verify(readRepo, never()).upsertRead(m2, medico.userId());
        verify(notifier).broadcastMessage(eq(convId), any());
    }

    @Test
    @DisplayName("markAsRead con set vacío no emite WS event")
    void markAsReadEmptySetIsNoop() {
        UUID m1 = UUID.randomUUID();
        Message foreign = Message.builder().id(m1).conversationId(UUID.randomUUID()).build();

        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(true);
        when(messageRepo.findByIds(List.of(m1))).thenReturn(List.of(foreign));

        service.markAsRead(convId, List.of(m1), medico);

        verify(readRepo, never()).upsertRead(any(), any());
        verify(notifier, never()).broadcastMessage(any(), any());
    }
}
