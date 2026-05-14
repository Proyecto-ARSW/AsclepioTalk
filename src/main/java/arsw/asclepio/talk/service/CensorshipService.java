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
import java.util.Locale;
import java.util.Map;
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

    // ── Tabla de equivalencias leet-speak ───────────────────────────────────
    // Cada letra mapea a una clase de caracteres que la representan en texto
    // ofuscado. Esto permite que la palabra censurada "tonto" matchee también
    // "T0nt0", "t0NT0", "T0nt@" (cuando 'o' es la 'a' equivocada — no aplica
    // aquí — pero ilustra el principio). Reglas:
    //   • Mayúsculas/minúsculas se manejan con CASE_INSENSITIVE en el Pattern.
    //   • Cada sustitución es 1↔1 en longitud, así los offsets del match se
    //     conservan y replaceAll() funciona sin recalcular posiciones.
    //   • Sólo cubrimos sustituciones populares y conservadoras — agregar más
    //     elevaría el riesgo de falsos positivos en lenguaje médico legítimo.
    private static final Map<Character, String> LEET_CLASS = Map.ofEntries(
            Map.entry('a', "[a4@]"),
            Map.entry('b', "[b8]"),
            Map.entry('e', "[e3]"),
            Map.entry('g', "[g9]"),
            Map.entry('i', "[i1!|]"),
            Map.entry('l', "[l1!|]"),
            Map.entry('o', "[o0]"),
            Map.entry('s', "[s5$]"),
            Map.entry('t', "[t7]"),
            Map.entry('z', "[z2]")
    );

    // Límite de palabra que sí tolera dígitos como parte de la palabra ofuscada.
    // \b nativo trata el cambio dígito↔letra como límite, por lo que "t0nt0"
    // produciría un \b interno y rompería el match completo. Usamos lookarounds
    // sobre la unión {letras Unicode, dígitos} para que la palabra se considere
    // "una sola unidad" independientemente de su contenido alfanumérico.
    private static final String LEFT_BOUNDARY = "(?<![\\p{L}\\d])";
    private static final String RIGHT_BOUNDARY = "(?![\\p{L}\\d])";

    private final CensoredWordRepository wordRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    // Aplica censura automática al contenido del mensaje.
    // Retorna el texto con palabras prohibidas reemplazadas por *****.
    public CensorResult censor(String content) {
        if (content == null || content.isBlank()) {
            return new CensorResult(content, false);
        }
        Set<String> words = loadActiveWords();
        if (words.isEmpty()) {
            return new CensorResult(content, false);
        }

        String censored = content;
        boolean wasCensored = false;

        for (String word : words) {
            Pattern pattern = buildLeetPattern(word);
            String replaced = pattern.matcher(censored).replaceAll(REPLACEMENT);
            if (!replaced.equals(censored)) {
                censored = replaced;
                wasCensored = true;
            }
        }

        return new CensorResult(censored, wasCensored);
    }

    // Construye un Pattern que matchea la palabra censurada en sus variantes
    // de capitalización y leet-speak. Ej: para "tonto" genera:
    //   (?<![\p{L}\d])[t7][o0][nN][t7][o0](?![\p{L}\d])
    // (la 'n' va escapada literal porque no tiene equivalente leet común).
    // CASE_INSENSITIVE + UNICODE_CASE garantiza que las letras sin entrada en
    // la tabla leet sigan matcheando mayúsculas/minúsculas correctamente.
    private static Pattern buildLeetPattern(String word) {
        StringBuilder regex = new StringBuilder(word.length() * 4);
        regex.append(LEFT_BOUNDARY);
        for (char c : word.toLowerCase(Locale.ROOT).toCharArray()) {
            String mapped = LEET_CLASS.get(c);
            if (mapped != null) {
                regex.append(mapped);
            } else {
                // Pattern.quote escapa metacaracteres si la palabra los contiene
                // (espacios, puntos, guiones). Para letras puras es un no-op.
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append(RIGHT_BOUNDARY);
        return Pattern.compile(regex.toString(),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
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
        String normalized = word.trim().toLowerCase(Locale.ROOT);

        var existing = wordRepo.findByWordIgnoreCase(normalized);
        if (existing.isPresent()) {
            CensoredWord found = existing.get();
            if (found.isActive()) {
                throw new DuplicateWordException(normalized);
            }
            // Reactivación: existía como soft-deleted, la rehabilitamos.
            found.setActive(true);
            CensoredWord reactivated = wordRepo.save(found);
            invalidateCache();
            log.info("Palabra censurada reactivada: '{}' por {}", normalized, admin.fullName());
            return CensoredWordResponse.from(reactivated);
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
