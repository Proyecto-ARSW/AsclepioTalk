package arsw.asclepio.talk.service;

import arsw.asclepio.talk.domain.censorship.CensoredWordRepository;
import arsw.asclepio.talk.dto.response.CensoredWordResponse;
import arsw.asclepio.talk.exception.DuplicateWordException;
import arsw.asclepio.talk.security.UserPrincipal;
import arsw.asclepio.talk.service.CensorshipService.CensorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Daniel Useche
@ExtendWith(MockitoExtension.class)
class CensorshipServiceTest {

    @Mock CensoredWordRepository wordRepo;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    @InjectMocks CensorshipService service;

    @BeforeEach
    void setupRedis() {
        // lenient porque algunos tests hacen short-circuit antes de tocar Redis
        // (contenido vacío, palabra duplicada detectada por DB). Strict mode
        // marcaría esos casos como UnnecessaryStubbing, pero el stub es válido
        // para los tests que sí leen el cache.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("Censura automática reemplaza palabras prohibidas por *****")
    void censorReplacesWord() {
        when(valueOps.get("talk:censored_words")).thenReturn(null);
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("mierda", "idiota"));

        CensorResult result = service.censor("qué mierda de día");

        assertThat(result.wasCensored()).isTrue();
        assertThat(result.displayContent()).isEqualTo("qué ***** de día");
    }

    @Test
    @DisplayName("Texto sin palabras prohibidas pasa sin alteración")
    void cleanTextPassesThrough() {
        when(valueOps.get("talk:censored_words")).thenReturn(Set.of("mierda"));

        CensorResult result = service.censor("Buenos días doctor");

        assertThat(result.wasCensored()).isFalse();
        assertThat(result.displayContent()).isEqualTo("Buenos días doctor");
    }

    @Test
    @DisplayName("Censura es insensible a mayúsculas")
    void censorIsCaseInsensitive() {
        when(valueOps.get("talk:censored_words")).thenReturn(null);
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("idiota"));

        CensorResult result = service.censor("No seas IDIOTA");

        assertThat(result.wasCensored()).isTrue();
        assertThat(result.displayContent()).isEqualTo("No seas *****");
    }

    @Test
    @DisplayName("Censura detecta mayúsculas mezcladas en mitad de palabra")
    void censorDetectsMixedCase() {
        when(valueOps.get("talk:censored_words")).thenReturn(null);
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("tonto"));

        CensorResult result = service.censor("Eres ToNtO de remate");

        assertThat(result.wasCensored()).isTrue();
        assertThat(result.displayContent()).isEqualTo("Eres ***** de remate");
    }

    @Test
    @DisplayName("Censura sustituye letras por dígitos leet (t0nt0)")
    void censorDetectsLeetDigits() {
        when(valueOps.get("talk:censored_words")).thenReturn(null);
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("tonto"));

        CensorResult result = service.censor("ese t0nt0 no entiende");

        assertThat(result.wasCensored()).isTrue();
        assertThat(result.displayContent()).isEqualTo("ese ***** no entiende");
    }

    @Test
    @DisplayName("Censura combina mayúsculas y leet en la misma palabra (T0Nt@)")
    void censorDetectsMixedLeetAndCase() {
        when(valueOps.get("talk:censored_words")).thenReturn(null);
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("tonta"));

        CensorResult result = service.censor("No seas T0Nt@ por favor");

        assertThat(result.wasCensored()).isTrue();
        assertThat(result.displayContent()).isEqualTo("No seas ***** por favor");
    }

    @Test
    @DisplayName("Censura respeta límites: 'idiotas' no matchea 'idiota' (sufijo)")
    void censorRespectsWordBoundary() {
        when(valueOps.get("talk:censored_words")).thenReturn(null);
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("idiota"));

        // Importante: si la palabra censurada es exactamente "idiota",
        // "idiotas" debe quedar intacto. Si el admin quiere ambas, agrega ambas.
        CensorResult result = service.censor("los idiotas no");

        assertThat(result.wasCensored()).isFalse();
        assertThat(result.displayContent()).isEqualTo("los idiotas no");
    }

    @Test
    @DisplayName("Censura múltiples ocurrencias en el mismo mensaje")
    void censorAllOccurrences() {
        when(valueOps.get("talk:censored_words")).thenReturn(null);
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("tonto"));

        CensorResult result = service.censor("tonto y otra vez T0nT0");

        assertThat(result.wasCensored()).isTrue();
        assertThat(result.displayContent()).isEqualTo("***** y otra vez *****");
    }

    @Test
    @DisplayName("Censura múltiples palabras prohibidas distintas")
    void censorMultipleWords() {
        when(valueOps.get("talk:censored_words")).thenReturn(null);
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("tonto", "idiota"));

        CensorResult result = service.censor("tonto e idiota");

        assertThat(result.wasCensored()).isTrue();
        assertThat(result.displayContent()).isEqualTo("***** e *****");
    }

    @Test
    @DisplayName("Texto null o vacío no se procesa")
    void emptyContentShortCircuits() {
        CensorResult resultNull = service.censor(null);
        CensorResult resultBlank = service.censor("   ");

        assertThat(resultNull.wasCensored()).isFalse();
        assertThat(resultBlank.wasCensored()).isFalse();
        // Sin acceso a Redis cuando el contenido no amerita procesamiento.
        verifyNoInteractions(valueOps);
    }

    @Test
    @DisplayName("Agregar palabra duplicada activa lanza DuplicateWordException")
    void duplicateWordThrows() {
        var existingWord = mock(arsw.asclepio.talk.domain.censorship.CensoredWord.class);
        when(existingWord.isActive()).thenReturn(true);
        when(wordRepo.findByWordIgnoreCase("idiota")).thenReturn(Optional.of(existingWord));

        UserPrincipal admin = new UserPrincipal(
                UUID.randomUUID(), "admin@hospital.com", "ADMIN", "Carlos", "López", 1
        );

        assertThatThrownBy(() -> service.addWord("idiota", admin))
                .isInstanceOf(DuplicateWordException.class);
    }

    @Test
    @DisplayName("Usa caché Redis si ya está disponible")
    void usesRedisCache() {
        Set<String> cached = Set.of("mierda");
        when(valueOps.get("talk:censored_words")).thenReturn(cached);

        service.censor("sin groserías");

        verifyNoInteractions(wordRepo);
    }

    @Test
    @DisplayName("Cache corrupto se autorrecupera borrando la clave y leyendo desde DB")
    void corruptCacheRecoversFromDb() {
        // Simula la entrada vieja en Redis sin type wrapper de Jackson:
        // GenericJackson2JsonRedisSerializer lanza SerializationException al deserializar.
        when(valueOps.get("talk:censored_words"))
                .thenThrow(new SerializationException("Could not resolve type id 'mierda'"));
        when(wordRepo.findAllActiveWords()).thenReturn(List.of("mierda"));

        CensorResult result = service.censor("qué mierda");

        assertThat(result.wasCensored()).isTrue();
        assertThat(result.displayContent()).isEqualTo("qué *****");
        verify(redisTemplate).delete("talk:censored_words");
        verify(valueOps).set(eq("talk:censored_words"), any(Set.class), any());
    }
}
