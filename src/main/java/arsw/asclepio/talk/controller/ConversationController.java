package arsw.asclepio.talk.controller;

import arsw.asclepio.talk.dto.request.AddParticipantRequest;
import arsw.asclepio.talk.dto.request.CreateConversationRequest;
import arsw.asclepio.talk.dto.request.UpdateConversationRequest;
import arsw.asclepio.talk.dto.response.ConversationResponse;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Daniel Useche
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest req,
                                                        Authentication auth) {
        UserPrincipal user = principal(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.create(req, user));
    }

    @GetMapping
    public List<ConversationResponse> list(Authentication auth) {
        return conversationService.listForUser(principal(auth));
    }

    @GetMapping("/{id}")
    public ConversationResponse getById(@PathVariable UUID id, Authentication auth) {
        return conversationService.getById(id, principal(auth));
    }

    @PutMapping("/{id}")
    public ConversationResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateConversationRequest req,
                                        Authentication auth) {
        return conversationService.update(id, req, principal(auth));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        conversationService.delete(id, principal(auth));
    }

    @PostMapping("/{id}/participants")
    public ConversationResponse addParticipant(@PathVariable UUID id,
                                                @Valid @RequestBody AddParticipantRequest req,
                                                Authentication auth) {
        return conversationService.addParticipant(id, req, principal(auth));
    }

    @DeleteMapping("/{id}/participants/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(@PathVariable UUID id,
                                   @PathVariable UUID userId,
                                   Authentication auth) {
        conversationService.removeParticipant(id, userId, principal(auth));
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
