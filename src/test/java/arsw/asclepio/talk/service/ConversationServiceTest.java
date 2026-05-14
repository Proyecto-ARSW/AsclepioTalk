package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.conversation.*;
import arsw.asclepio.talk.dto.request.CreateConversationRequest;
import arsw.asclepio.talk.dto.response.ConversationResponse;
import arsw.asclepio.talk.exception.ForbiddenActionException;
import arsw.asclepio.talk.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Daniel Useche
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ConversationRepository conversationRepo;
    @Mock ParticipantRepository participantRepo;
    @Mock WebSocketNotifier notifier;

    @InjectMocks ConversationService service;

    private final UserPrincipal medico = new UserPrincipal(
            UUID.randomUUID(), "medico@hosp.com", "MEDICO", "Ana", "García", 1
    );

    private final UserPrincipal paciente = new UserPrincipal(
            UUID.randomUUID(), "paciente@hosp.com", "PACIENTE", "Luis", "Pérez", 1
    );

    @Test
    @DisplayName("MEDICO puede crear chat individual")
    void medicoCanCreateIndividual() {
        UUID otherId = UUID.randomUUID();
        when(conversationRepo.findIndividualBetween(any(), any(), any())).thenReturn(Optional.empty());

        Conversation saved = Conversation.builder()
                .id(UUID.randomUUID())
                .type(ConversationType.INDIVIDUAL)
                .hospitalId(1)
                .createdBy(medico.userId())
                .participants(new ArrayList<>())
                .build();

        when(conversationRepo.save(any())).thenReturn(saved);
        // Tras refactor: ConversationService construye la response consultando
        // participants directamente al repo (no re-leyendo la conversation),
        // para evitar el bug del @OneToMany vacío en el cache de Hibernate.
        when(participantRepo.findActiveByConversation(saved.getId())).thenReturn(List.of());

        CreateConversationRequest req = new CreateConversationRequest(
                ConversationType.INDIVIDUAL, null, null,
                List.of(new CreateConversationRequest.ParticipantInput(otherId, "Otro Usuario", "MEDICO")),
                false
        );

        ConversationResponse response = service.create(req, medico);

        assertThat(response).isNotNull();
        verify(conversationRepo).save(any());
    }

    @Test
    @DisplayName("PACIENTE no puede crear conversaciones")
    void pacienteCannotCreate() {
        UUID participantId = UUID.randomUUID();
        CreateConversationRequest req = new CreateConversationRequest(
                ConversationType.GROUP, "Grupo", null,
                List.of(new CreateConversationRequest.ParticipantInput(participantId, "Usuario", "MEDICO")),
                false
        );

        assertThatThrownBy(() -> service.create(req, paciente))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    @DisplayName("Chat individual existente es retornado sin crear uno nuevo")
    void existingIndividualIsReused() {
        UUID otherId = UUID.randomUUID();
        Conversation existing = Conversation.builder()
                .id(UUID.randomUUID())
                .type(ConversationType.INDIVIDUAL)
                .hospitalId(1)
                .createdBy(medico.userId())
                .participants(new ArrayList<>())
                .build();

        when(conversationRepo.findIndividualBetween(1, medico.userId(), otherId))
                .thenReturn(Optional.of(existing));

        CreateConversationRequest req = new CreateConversationRequest(
                ConversationType.INDIVIDUAL, null, null,
                List.of(new CreateConversationRequest.ParticipantInput(otherId, "Otro Usuario", "MEDICO")),
                false
        );

        ConversationResponse response = service.create(req, medico);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(conversationRepo, never()).save(any());
    }

    @Test
    @DisplayName("Grupo sin nombre lanza excepción")
    void groupWithoutNameThrows() {
        UUID participantId = UUID.randomUUID();
        CreateConversationRequest req = new CreateConversationRequest(
                ConversationType.GROUP, null, null,
                List.of(new CreateConversationRequest.ParticipantInput(participantId, "Usuario", "MEDICO")),
                false
        );

        assertThatThrownBy(() -> service.create(req, medico))
                .isInstanceOf(ForbiddenActionException.class)
                .hasMessageContaining("nombre");
    }
}
