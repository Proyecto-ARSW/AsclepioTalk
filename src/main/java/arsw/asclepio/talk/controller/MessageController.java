package arsw.asclepio.talk.controller;

import arsw.asclepio.talk.dto.request.AddReactionRequest;
import arsw.asclepio.talk.dto.request.EditMessageRequest;
import arsw.asclepio.talk.dto.request.MarkAsReadRequest;
import arsw.asclepio.talk.dto.request.SendMessageRequest;
import arsw.asclepio.talk.dto.response.MessageResponse;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.MessageService;
import arsw.asclepio.talk.service.ReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// Daniel Useche
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ReactionService reactionService;

    @GetMapping
    public Page<MessageResponse> list(
            @PathVariable UUID conversationId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            Authentication auth) {
        return messageService.list(conversationId, principal(auth), pageable);
    }

    @PostMapping
    public ResponseEntity<MessageResponse> send(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest req,
            Authentication auth) {
        MessageResponse response = messageService.send(conversationId, req, principal(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Variante multipart: el cliente manda `data` (JSON con SendMessageRequest)
    // y `file` (binario). Spring deserializa `data` como @RequestPart porque
    // el cliente le pone content-type=application/json al blob; sin eso, llega
    // como String y la validacion @Valid no se aplica.
    @PostMapping(path = "/with-attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> sendWithAttachment(
            @PathVariable UUID conversationId,
            @Valid @RequestPart("data") SendMessageRequest req,
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        MessageResponse response = messageService.send(conversationId, req, file, principal(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{messageId}")
    public MessageResponse edit(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest req,
            Authentication auth) {
        return messageService.edit(conversationId, messageId, req, principal(auth));
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            Authentication auth) {
        messageService.delete(conversationId, messageId, principal(auth));
    }

    @PatchMapping("/{messageId}/censor")
    public MessageResponse censor(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            Authentication auth) {
        return messageService.censorManually(conversationId, messageId, principal(auth));
    }

    // ─── Read receipts ─────────────────────────────────────────────────────────

    // Devuelve 204 — la operación es idempotente y no necesita retornar payload.
    // El cliente recibirá la actualización vía WS (MESSAGE_READ).
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
            @PathVariable UUID conversationId,
            @Valid @RequestBody MarkAsReadRequest req,
            Authentication auth) {
        messageService.markAsRead(conversationId, req.messageIds(), principal(auth));
    }

    // ─── Reactions ─────────────────────────────────────────────────────────────

    @PostMapping("/{messageId}/reactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addReaction(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @Valid @RequestBody AddReactionRequest req,
            Authentication auth) {
        reactionService.add(conversationId, messageId, req.emoji(), principal(auth));
    }

    // El emoji va en el path para que el DELETE sea idempotente y RESTful
    // (sin body). Frontend debe URL-encode el emoji.
    @DeleteMapping("/{messageId}/reactions/{emoji}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeReaction(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @PathVariable String emoji,
            Authentication auth) {
        reactionService.remove(conversationId, messageId, emoji, principal(auth));
    }

    // ─── Pin / Unpin ───────────────────────────────────────────────────────────

    // PATCH toggle: simplifica al cliente (no necesita saber el estado actual).
    @PatchMapping("/{messageId}/pin")
    public MessageResponse togglePin(
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            Authentication auth) {
        return messageService.togglePin(conversationId, messageId, principal(auth));
    }

    // Listado de fijados. Lo mantenemos bajo el prefijo /messages porque
    // son mensajes — un drawer en la UI los lista por encima del scroll regular.
    @GetMapping("/pinned")
    public List<MessageResponse> getPinned(
            @PathVariable UUID conversationId,
            Authentication auth) {
        return messageService.getPinned(conversationId, principal(auth));
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
