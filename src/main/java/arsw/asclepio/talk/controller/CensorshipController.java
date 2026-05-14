package arsw.asclepio.talk.controller;

import arsw.asclepio.talk.dto.request.AddCensoredWordRequest;
import arsw.asclepio.talk.dto.response.CensoredWordResponse;
import arsw.asclepio.talk.exception.ForbiddenActionException;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.CensorshipService;
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
@RequestMapping("/api/v1/censorship/words")
@RequiredArgsConstructor
public class CensorshipController {

    private final CensorshipService censorshipService;

    @GetMapping
    public List<CensoredWordResponse> list(Authentication auth) {
        requireAdmin(auth);
        return censorshipService.listAll();
    }

    @PostMapping
    public ResponseEntity<CensoredWordResponse> add(
            @Valid @RequestBody AddCensoredWordRequest req,
            Authentication auth) {
        requireAdmin(auth);
        CensoredWordResponse response = censorshipService.addWord(req.word(), principal(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id, Authentication auth) {
        requireAdmin(auth);
        censorshipService.deactivateWord(id);
    }

    private void requireAdmin(Authentication auth) {
        if (!principal(auth).isAdmin()) {
            throw new ForbiddenActionException("Solo ADMIN puede gestionar la lista de censura");
        }
    }

    private UserPrincipal principal(Authentication auth) {
        return (UserPrincipal) auth.getPrincipal();
    }
}
