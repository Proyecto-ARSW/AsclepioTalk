package arsw.asclepio.talk.dto.response;

import arsw.asclepio.talk.domain.censorship.CensoredWord;

import java.time.LocalDateTime;
import java.util.UUID;

// Daniel Useche
public record CensoredWordResponse(
        UUID id,
        String word,
        String addedByName,
        boolean active,
        LocalDateTime createdAt
) {
    public static CensoredWordResponse from(CensoredWord w) {
        return new CensoredWordResponse(
                w.getId(),
                w.getWord(),
                w.getAddedByName(),
                w.isActive(),
                w.getCreatedAt()
        );
    }
}
