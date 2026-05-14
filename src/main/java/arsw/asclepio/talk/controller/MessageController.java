package arsw.asclepio.talk.controller;

import arsw.asclepio.talk.dto.request.EditMessageRequest;
import arsw.asclepio.talk.dto.request.SendMessageRequest;
import arsw.asclepio.talk.dto.response.MessageResponse;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Daniel Useche
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

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

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
