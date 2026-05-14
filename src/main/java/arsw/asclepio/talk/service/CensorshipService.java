package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.censorship.CensoredWord;
import arsw.asclepio.talk.domain.censorship.CensoredWordRepository;
import arsw.asclepio.talk.dto.response.CensoredWordResponse;
import arsw.asclepio.talk.exception.DuplicateWordException;
import arsw.asclepio.talk.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

// Daniel Useche
@Slf4j
@Service
@RequiredArgsConstructor
public class CensorshipService {

    private static final String CACHE_KEY = "talk:censored_words";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String REPLACEMENT = "*****";

    private final CensoredWordRepository wordRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    // Aplica censura automática al contenido del mensaje.
    // Retorna el texto con palabras prohibidas reemplazadas por *****.
    public CensorResult censor(String content) {
        Set<String> words = loadActiveWords();
        if (words.isEmpty()) {
            return new CensorResult(content, false);
        }

        String censored = content;
        boolean wasCensored = false;

        for (String word : words) {
            // Coincidencia por palabra completa, ignorando mayúsculas/minúsculas y acentos simples
            Pattern pattern = Pattern.compile(
                    "(?i)\\b" + Pattern.quote(word) + "\\b"
            );
            String replaced = pattern.matcher(censored).replaceAll(REPLACEMENT);
            if (!replaced.equals(censored)) {
                censored = replaced;
                wasCensored = true;
            }
        }

        return new CensorResult(censored, wasCensored);
    }

    @Transactional(readOnly = true)
    public List<CensoredWordResponse> listAll() {
        return wordRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(CensoredWordResponse::from)
                .toList();
    }

    @Transactional
    public CensoredWordResponse addWord(String word, UserPrincipal admin) {
        String normalized = word.trim().toLowerCase();

        wordRepo.findByWordIgnoreCase(normalized).ifPresent(existing -> {
            if (existing.isActive()) throw new DuplicateWordException(normalized);
            // Si existía y estaba desactivada, la reactiva
            existing.setActive(true);
            wordRepo.save(existing);
            invalidateCache();
        });

        if (wordRepo.findByWordIgnoreCase(normalized).isPresent()) {
            return CensoredWordResponse.from(wordRepo.findByWordIgnoreCase(normalized).get());
        }

        CensoredWord saved = wordRepo.save(CensoredWord.builder()
                .word(normalized)
                .addedBy(admin.userId())
                .addedByName(admin.fullName())
                .build());

        invalidateCache();
        log.info("Palabra censurada agregada: '{}' por {}", normalized, admin.fullName());
        return CensoredWordResponse.from(saved);
    }

    @Transactional
    public void deactivateWord(UUID wordId) {
        wordRepo.findById(wordId).ifPresent(w -> {
            w.setActive(false);
            wordRepo.save(w);
            invalidateCache();
            log.info("Palabra censurada desactivada: '{}'", w.getWord());
        });
    }

    @SuppressWarnings("unchecked")
    private Set<String> loadActiveWords() {
        try {
            Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached instanceof Set<?> set) {
                return (Set<String>) set;
            }
        } catch (org.springframework.data.redis.serializer.SerializationException ex) {
            // Cache con datos incompatibles (e.g. clave vieja sin type wrapper de Jackson).
            // Borramos la entrada para que la siguiente escritura quede en formato correcto.
            log.warn("Cache de palabras censuradas corrupto, recargando desde DB", ex);
            try { redisTemplate.delete(CACHE_KEY); } catch (Exception ignore) {}
        }

        List<String> words = wordRepo.findAllActiveWords();
        // HashSet (no final) — GenericJackson2JsonRedisSerializer agrega el type wrapper
        // requerido para deserializar. Set.copyOf(...) producía un ImmutableCollections$SetN
        // final, que el serializer no envolvía y rompía la lectura posterior.
        Set<String> wordSet = new HashSet<>(words);
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, wordSet, CACHE_TTL);
        } catch (Exception ex) {
            log.warn("No se pudo escribir cache de palabras censuradas, se sirve desde DB", ex);
        }
        return wordSet;
    }

    private void invalidateCache() {
        redisTemplate.delete(CACHE_KEY);
    }

    // Resultado de aplicar la censura automática
    public record CensorResult(String displayContent, boolean wasCensored) {}
}
