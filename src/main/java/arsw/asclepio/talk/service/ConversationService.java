package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.conversation.*;
import arsw.asclepio.talk.dto.request.AddParticipantRequest;
import arsw.asclepio.talk.dto.request.CreateConversationRequest;
import arsw.asclepio.talk.dto.request.UpdateConversationRequest;
import arsw.asclepio.talk.dto.response.ConversationResponse;
import arsw.asclepio.talk.exception.ConversationNotFoundException;
import arsw.asclepio.talk.exception.ForbiddenActionException;
import arsw.asclepio.talk.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Daniel Useche
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepo;
    private final ParticipantRepository participantRepo;
    private final WebSocketNotifier notifier;

    @Transactional
    public ConversationResponse create(CreateConversationRequest req, UserPrincipal creator) {
        if (!creator.canCreateConversation()) {
            throw new ForbiddenActionException("Solo MEDICO y ADMIN pueden crear conversaciones");
        }

        if (req.anonymous() && req.type() == ConversationType.INDIVIDUAL) {
            throw new ForbiddenActionException("Los chats individuales no pueden ser anónimos");
        }

        if (req.type() == ConversationType.INDIVIDUAL) {
            return createIndividual(req, creator);
        }
        return createGroup(req, creator);
    }

    private ConversationResponse createIndividual(CreateConversationRequest req, UserPrincipal creator) {
        CreateConversationRequest.ParticipantInput other = req.participants().get(0);

        // Idempotente: retorna el chat existente si ya hay uno entre estos dos usuarios
        return conversationRepo
                .findIndividualBetween(creator.hospitalId(), creator.userId(), other.userId())
                .map(c -> ConversationResponse.from(c, creator.userId()))
                .orElseGet(() -> {
                    Conversation conv = conversationRepo.save(Conversation.builder()
                            .type(ConversationType.INDIVIDUAL)
                            .hospitalId(creator.hospitalId())
                            .createdBy(creator.userId())
                            .build());

                    addParticipant(conv, creator.userId(), creator.fullName(), creator.rol());
                    addParticipant(conv, other.userId(), other.userName(), other.userRol());

                    ConversationResponse response = ConversationResponse.from(
                            conversationRepo.findById(conv.getId()).orElseThrow(),
                            creator.userId()
                    );
                    notifier.notifyNewConversation(other.userId(), response);
                    return response;
                });
    }

    private ConversationResponse createGroup(CreateConversationRequest req, UserPrincipal creator) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ForbiddenActionException("Los grupos deben tener un nombre");
        }

        Conversation conv = conversationRepo.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name(req.name())
                .description(req.description())
                .hospitalId(creator.hospitalId())
                .createdBy(creator.userId())
                .anonymous(req.anonymous())
                .build());

        addParticipant(conv, creator.userId(), creator.fullName(), creator.rol());
        req.participants().forEach(p ->
                addParticipant(conv, p.userId(), p.userName(), p.userRol())
        );

        ConversationResponse response = ConversationResponse.from(
                conversationRepo.findById(conv.getId()).orElseThrow(),
                creator.userId()
        );

        // Notificar a todos los invitados
        req.participants().forEach(p -> notifier.notifyNewConversation(p.userId(), response));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForUser(UserPrincipal user) {
        return conversationRepo
                .findActiveByHospitalAndUser(user.hospitalId(), user.userId())
                .stream()
                .map(c -> ConversationResponse.from(c, user.userId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getById(UUID id, UserPrincipal user) {
        Conversation conv = findActive(id);
        requireParticipant(conv.getId(), user.userId());
        return ConversationResponse.from(conv, user.userId());
    }

    @Transactional
    public ConversationResponse update(UUID id, UpdateConversationRequest req, UserPrincipal user) {
        Conversation conv = findActive(id);

        if (conv.getType() == ConversationType.INDIVIDUAL) {
            throw new ForbiddenActionException("No se puede editar un chat individual");
        }
        requireEditPermission(conv, user);

        if (req.name() != null) conv.setName(req.name());
        if (req.description() != null) conv.setDescription(req.description());
        conv.setUpdatedAt(LocalDateTime.now());

        return ConversationResponse.from(conversationRepo.save(conv), user.userId());
    }

    @Transactional
    public void delete(UUID id, UserPrincipal user) {
        Conversation conv = findActive(id);
        requireEditPermission(conv, user);
        conv.setActive(false);
        conversationRepo.save(conv);
        log.info("Conversación {} eliminada por {}", id, user.userId());
    }

    @Transactional
    public ConversationResponse addParticipant(UUID convId, AddParticipantRequest req, UserPrincipal user) {
        Conversation conv = findActive(convId);
        requireEditPermission(conv, user);

        // Solo MEDICO/ADMIN pueden ser agregados a grupos; PACIENTE solo en individuales
        if (conv.getType() == ConversationType.GROUP && "PACIENTE".equals(req.userRol())) {
            throw new ForbiddenActionException("Los pacientes no pueden participar en grupos");
        }

        addParticipant(conv, req.userId(), req.userName(), req.userRol());
        ConversationResponse response = ConversationResponse.from(conversationRepo.findById(convId).orElseThrow(), user.userId());
        notifier.notifyNewConversation(req.userId(), response);
        return response;
    }

    @Transactional
    public void removeParticipant(UUID convId, UUID targetUserId, UserPrincipal user) {
        Conversation conv = findActive(convId);
        requireEditPermission(conv, user);

        participantRepo.findByConversationAndUser(convId, targetUserId).ifPresent(p -> {
            p.setActive(false);
            participantRepo.save(p);
        });
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private void addParticipant(Conversation conv, UUID userId, String userName, String userRol) {
        ParticipantId pid = new ParticipantId(conv.getId(), userId);
        if (participantRepo.findById(pid).isEmpty()) {
            participantRepo.save(Participant.builder()
                    .id(pid)
                    .conversation(conv)
                    .userName(userName)
                    .userRol(userRol)
                    .build());
        }
    }

    private Conversation findActive(UUID id) {
        return conversationRepo.findById(id)
                .filter(Conversation::isActive)
                .orElseThrow(() -> new ConversationNotFoundException(id));
    }

    private void requireParticipant(UUID convId, UUID userId) {
        if (!participantRepo.isActiveParticipant(convId, userId)) {
            throw new arsw.asclepio.talk.exception.NotParticipantException(userId, convId);
        }
    }

    private void requireEditPermission(Conversation conv, UserPrincipal user) {
        boolean isCreator = conv.getCreatedBy().equals(user.userId());
        boolean isAdmin = user.isAdmin();
        if (!isCreator && !isAdmin) {
            throw new ForbiddenActionException("Solo el creador o un ADMIN pueden modificar esta conversación");
        }
    }
}
