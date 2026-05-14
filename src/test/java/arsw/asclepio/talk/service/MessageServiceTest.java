package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.conversation.ParticipantRepository;
import arsw.asclepio.talk.domain.message.Message;
import arsw.asclepio.talk.domain.message.MessageRepository;
import arsw.asclepio.talk.dto.request.SendMessageRequest;
import arsw.asclepio.talk.dto.response.MessageResponse;
import arsw.asclepio.talk.exception.ForbiddenActionException;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.CensorshipService.CensorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Daniel Useche
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock MessageRepository messageRepo;
    @Mock ParticipantRepository participantRepo;
    @Mock CensorshipService censorshipService;
    @Mock WebSocketNotifier notifier;

    @InjectMocks MessageService service;

    private final UUID convId = UUID.randomUUID();

    private final UserPrincipal medico = new UserPrincipal(
            UUID.randomUUID(), "dr@hosp.com", "MEDICO", "Pedro", "Romero", 1
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

        MessageResponse response = service.send(convId, new SendMessageRequest("qué mierda"), medico);

        assertThat(response.autoCensored()).isTrue();
        assertThat(response.contentDisplay()).isEqualTo("qué *****");
    }

    @Test
    @DisplayName("No participante no puede enviar mensaje")
    void nonParticipantCannotSend() {
        when(participantRepo.isActiveParticipant(convId, medico.userId())).thenReturn(false);

        assertThatThrownBy(() -> service.send(convId, new SendMessageRequest("hola"), medico))
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
}
