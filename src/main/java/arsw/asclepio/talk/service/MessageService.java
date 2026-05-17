package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.conversation.ParticipantRepository;
import arsw.asclepio.talk.domain.message.Message;
import arsw.asclepio.talk.domain.message.MessageAttachment;
import arsw.asclepio.talk.domain.message.MessageAttachmentRepository;
import arsw.asclepio.talk.domain.message.MessageReactionRepository;
import arsw.asclepio.talk.domain.message.MessageReadRepository;
import arsw.asclepio.talk.domain.message.MessageRepository;
import arsw.asclepio.talk.dto.request.EditMessageRequest;
import arsw.asclepio.talk.dto.request.SendMessageRequest;
import arsw.asclepio.talk.dto.response.AttachmentResponse;
import arsw.asclepio.talk.dto.response.MessageResponse;
import arsw.asclepio.talk.dto.response.ReactionGroupResponse;
import arsw.asclepio.talk.dto.response.ReadReceiptResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Daniel Useche
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepo;
    private final MessageReactionRepository reactionRepo;
    private final MessageReadRepository readRepo;
    private final MessageAttachmentRepository attachmentRepo;
    private final ParticipantRepository participantRepo;
    private final CensorshipService censorshipService;
    private final AttachmentStorageService storageService;
    private final WebSocketNotifier notifier;

    @Transactional
    public MessageResponse send(UUID conversationId, SendMessageRequest req, UserPrincipal sender) {
        return send(conversationId, req, null, sender);
    }

    // Sobrecarga con adjunto opcional. Cuando `file` es null, el flujo es
    // identico al de solo-texto. Si viene file, validamos el contenido (puede
    // estar vacio si solo se envia el archivo) y subimos a MinIO antes de
    // persistir el mensaje para que un fallo de storage no deje mensaje huerfano.
    @Transactional
    public MessageResponse send(UUID conversationId, SendMessageRequest req, MultipartFile file, UserPrincipal sender) {
        requireParticipant(conversationId, sender.userId());

        boolean hasFile = file != null && !file.isEmpty();
        String rawContent = req.content() == null ? "" : req.content();
        if (rawContent.isBlank() && !hasFile) {
            throw new IllegalArgumentException("El mensaje debe tener texto o un archivo adjunto");
        }

        // Validamos el reply ANTES de crear el mensaje. Si el padre no existe
        // o está en otra conversación, abortamos limpio — no creamos basura.
        Message parent = null;
        if (req.replyToMessageId() != null) {
            parent = messageRepo.findById(req.replyToMessageId())
                    .filter(p -> p.getConversationId().equals(conversationId))
                    .orElseThrow(() -> new MessageNotFoundException(req.replyToMessageId()));
        }

        // Sube primero — si truena, nada se persistio.
        AttachmentStorageService.StoredFile stored = hasFile
                ? storageService.upload(conversationId, file)
                : null;

        try {
            CensorResult censored = censorshipService.censor(rawContent);

            Message msg = messageRepo.save(Message.builder()
                    .conversationId(conversationId)
                    .senderId(sender.userId())
                    .senderName(sender.fullName())
                    .contentOriginal(rawContent)
                    .contentDisplay(censored.displayContent())
                    .autoCensored(censored.wasCensored())
                    .replyToMessageId(parent != null ? parent.getId() : null)
                    .build());

            MessageAttachment savedAttachment = null;
            if (stored != null) {
                savedAttachment = attachmentRepo.save(MessageAttachment.builder()
                        .messageId(msg.getId())
                        .storageKey(stored.storageKey())
                        .fileName(stored.fileName())
                        .mimeType(stored.mimeType())
                        .sizeBytes(stored.sizeBytes())
                        .uploadedBy(sender.userId())
                        .build());
            }

            // Mensaje recién creado: aún no tiene reacciones ni lecturas.
            AttachmentResponse attachmentDto = savedAttachment == null
                    ? null
                    : AttachmentResponse.from(savedAttachment, storageService.presignedGetUrl(savedAttachment.getStorageKey()));
            MessageResponse response = MessageResponse.from(
                    msg, sender, parent, Collections.emptyList(), Collections.emptyList(), attachmentDto);
            notifier.broadcastMessage(conversationId, WsMessageEvent.newMessage(conversationId, response));
            return response;
        } catch (RuntimeException ex) {
            // Compensacion: si fallo persistiendo el mensaje o el attachment,
            // borramos el blob para no dejarlo huerfano en MinIO.
            if (stored != null) {
                storageService.delete(stored.storageKey());
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> list(UUID conversationId, UserPrincipal user, Pageable pageable) {
        requireParticipant(conversationId, user.userId());

        Page<Message> page = messageRepo.findVisibleByConversation(conversationId, pageable);
        // Enriquecemos los DTOs de la página actual en lote (no de toda la conv):
        // 1 query por tipo de adorno (reactions, reads, reply parents), no N+1.
        return page.map(m -> enrichSingle(m, user, page.getContent()));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getPinned(UUID conversationId, UserPrincipal user) {
        requireParticipant(conversationId, user.userId());
        List<Message> pinned = messageRepo.findPinnedByConversation(conversationId);
        return enrichBatch(pinned, user);
    }

    @Transactional
    public MessageResponse edit(UUID conversationId, UUID messageId, EditMessageRequest req, UserPrincipal user) {
        Message msg = findVisible(messageId);
        requireParticipant(conversationId, user.userId());

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
        MessageResponse response = enrichSingleSelf(saved, user);
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

        // Best-effort: borramos el blob de MinIO si el mensaje tenia adjunto.
        // El soft-delete del mensaje ya ocurrio en DB; si MinIO falla, lo
        // registramos y seguimos — el frontend ya no mostrara el archivo
        // porque MessageResponse oculta el attachment cuando deleted=true.
        attachmentRepo.findFirstByMessageId(saved.getId())
                .ifPresent(att -> storageService.delete(att.getStorageKey()));

        MessageResponse response = enrichSingleSelf(saved, user);
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
        msg.setContentDisplay("[Mensaje censurado por " + user.fullName() + "]");

        Message saved = messageRepo.save(msg);
        log.info("Mensaje {} censurado manualmente por {}", messageId, user.fullName());

        MessageResponse response = enrichSingleSelf(saved, user);
        notifier.broadcastMessage(conversationId, WsMessageEvent.updatedMessage(conversationId, response));
        return response;
    }

    // ─── Read receipts ─────────────────────────────────────────────────────────

    @Transactional
    public void markAsRead(UUID conversationId, List<UUID> messageIds, UserPrincipal user) {
        requireParticipant(conversationId, user.userId());

        // Cargamos los mensajes para validar que pertenecen a la conversación
        // antes de insertar receipts — no queremos que un usuario marque como
        // leídos mensajes de otra conv aprovechando el endpoint.
        List<Message> messages = messageRepo.findByIds(messageIds);
        List<UUID> validIds = messages.stream()
                .filter(m -> m.getConversationId().equals(conversationId))
                .map(Message::getId)
                .toList();

        if (validIds.isEmpty()) {
            return;
        }

        // Insert idempotente con ON CONFLICT DO NOTHING — no falla si ya existe.
        for (UUID id : validIds) {
            readRepo.upsertRead(id, user.userId());
        }

        notifier.broadcastMessage(conversationId,
                WsMessageEvent.messagesRead(conversationId, user.userId(), user.fullName(), validIds));
    }

    // ─── Pinned messages ───────────────────────────────────────────────────────

    @Transactional
    public MessageResponse togglePin(UUID conversationId, UUID messageId, UserPrincipal user) {
        // Pin es restringido: solo MEDICO y ADMIN. Pinning tiene peso clínico
        // (orden, instrucción, recordatorio) — un PACIENTE no debería poder hacerlo.
        if (!user.canCensorMessage()) {
            throw new ForbiddenActionException("Solo MEDICO y ADMIN pueden fijar mensajes");
        }

        Message msg = findVisible(messageId);
        requireParticipant(conversationId, user.userId());
        if (!msg.getConversationId().equals(conversationId)) {
            throw new MessageNotFoundException(messageId);
        }

        boolean newPinned = !msg.isPinned();
        msg.setPinned(newPinned);
        msg.setPinnedBy(newPinned ? user.userId() : null);
        msg.setPinnedAt(newPinned ? LocalDateTime.now() : null);

        Message saved = messageRepo.save(msg);
        MessageResponse response = enrichSingleSelf(saved, user);

        notifier.broadcastMessage(conversationId,
                WsMessageEvent.messagePinned(conversationId, messageId, user.userId(), user.fullName(), newPinned));
        return response;
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    // Para un solo mensaje (edit/delete/censor/togglePin), traemos sus adornos
    // individualmente. Es O(1) en queries — basta porque no es un listado.
    private MessageResponse enrichSingleSelf(Message m, UserPrincipal user) {
        return enrichBatch(List.of(m), user).get(0);
    }

    // Mismo helper pero conociendo de antemano la lista a la que pertenece
    // (caso .map() sobre la página): si el padre del reply está en la misma
    // página, lo aprovechamos sin un fetch extra.
    private MessageResponse enrichSingle(Message m, UserPrincipal user, List<Message> pageContext) {
        Message parent = null;
        if (m.getReplyToMessageId() != null) {
            parent = pageContext.stream()
                    .filter(p -> p.getId().equals(m.getReplyToMessageId()))
                    .findFirst()
                    .orElseGet(() -> messageRepo.findById(m.getReplyToMessageId()).orElse(null));
        }
        // Reactions y reads se cargan por mensaje aquí — el .map() de Page los
        // pediría 1×N de todas formas. Para listas grandes mejor usar enrichBatch.
        List<ReactionGroupResponse> reactions = ReactionGroupResponse.groupBy(
                reactionRepo.findByMessageId(m.getId()));
        List<ReadReceiptResponse> reads = readRepo.findByMessageIds(List.of(m.getId())).stream()
                .map(r -> new ReadReceiptResponse(r.getId().getUserId(), null, r.getReadAt()))
                .toList();
        AttachmentResponse attachment = attachmentRepo.findFirstByMessageId(m.getId())
                .map(a -> AttachmentResponse.from(a, storageService.presignedGetUrl(a.getStorageKey())))
                .orElse(null);
        return MessageResponse.from(m, user, parent, reactions, reads, attachment);
    }

    // Versión batch eficiente: 4 queries totales para N mensajes
    // (reply parents + reactions + reads + attachments).
    private List<MessageResponse> enrichBatch(List<Message> messages, UserPrincipal user) {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> ids = messages.stream().map(Message::getId).toList();
        Set<UUID> parentIds = messages.stream()
                .map(Message::getReplyToMessageId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Message> parentsById = parentIds.isEmpty()
                ? Collections.emptyMap()
                : messageRepo.findByIds(parentIds).stream()
                        .collect(Collectors.toMap(Message::getId, m -> m));

        // Una sola query para todas las reactions del lote; agrupamos en memoria.
        Map<UUID, List<arsw.asclepio.talk.domain.message.MessageReaction>> rawReactions =
                reactionRepo.findByMessageIds(ids).stream()
                        .collect(Collectors.groupingBy(arsw.asclepio.talk.domain.message.MessageReaction::getMessageId));

        Map<UUID, List<ReadReceiptResponse>> readsByMsg =
                readRepo.findByMessageIds(ids).stream()
                        .collect(Collectors.groupingBy(
                                r -> r.getId().getMessageId(),
                                Collectors.mapping(
                                        r -> new ReadReceiptResponse(r.getId().getUserId(), null, r.getReadAt()),
                                        Collectors.toList())));

        // Adjuntos en una sola query — 1 maximo por mensaje hoy, pero usamos
        // groupingBy para no asumir; nos quedamos con el primero si por algun
        // motivo hubiera mas (defensiva).
        Map<UUID, MessageAttachment> attachmentByMsg =
                attachmentRepo.findByMessageIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                MessageAttachment::getMessageId,
                                a -> a,
                                (a, b) -> a));

        return messages.stream()
                .map(m -> {
                    Message parent = m.getReplyToMessageId() != null ? parentsById.get(m.getReplyToMessageId()) : null;
                    List<ReactionGroupResponse> reactions = ReactionGroupResponse.groupBy(
                            rawReactions.getOrDefault(m.getId(), Collections.emptyList()));
                    List<ReadReceiptResponse> reads = readsByMsg.getOrDefault(m.getId(), Collections.emptyList());
                    MessageAttachment att = attachmentByMsg.get(m.getId());
                    AttachmentResponse attachment = att == null
                            ? null
                            : AttachmentResponse.from(att, storageService.presignedGetUrl(att.getStorageKey()));
                    return MessageResponse.from(m, user, parent, reactions, reads, attachment);
                })
                .toList();
    }

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
