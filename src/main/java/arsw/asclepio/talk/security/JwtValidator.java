package arsw.asclepio.talk.security;

import arsw.asclepio.talk.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Valida el JWT emitido por AsclepioM1 usando el secreto compartido.
// Diseño robusto: distingue token expirado vs inválido vs claims incompletos
// para que la capa superior pueda responder con códigos HTTP/STOMP precisos
// y para que los logs apunten al motivo real cuando un usuario reporta
// "error de verificación" — facilita el diagnóstico operativo.
// Daniel Useche
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtValidator {

    private final JwtProperties jwtProperties;

    public UserPrincipal validate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String rol = claims.get("rol", String.class);
            String nombre = claims.get("nombre", String.class);
            String apellido = claims.get("apellido", String.class);
            Integer hospitalId = extractHospitalId(claims);

            // Defensa contra tokens emitidos por una versión del emisor que omita
            // claims obligatorios. Rechazamos explícitamente para no construir un
            // UserPrincipal incompleto que después rompa la lógica de roles.
            if (email == null || rol == null) {
                log.warn("JWT con claims incompletos — sub={} email={} rol={}",
                        userId, email, rol);
                throw new JwtException("Token incompleto: faltan claims obligatorios");
            }

            return new UserPrincipal(userId, email, rol, nombre, apellido, hospitalId);

        } catch (ExpiredJwtException e) {
            // Caso esperado tras 8h (JWT_EXPIRES_IN). El frontend debe re-loguear.
            log.debug("JWT expirado: {}", e.getMessage());
            throw new JwtException("Token expirado");
        } catch (SignatureException | MalformedJwtException e) {
            // Token forjado o JWT_SECRET desalineado entre M1 y Talk.
            log.warn("JWT con firma o formato inválido: {}", e.getMessage());
            throw new JwtException("Token inválido");
        } catch (JwtException e) {
            // Otros errores de JJWT — propagar mensaje específico.
            log.warn("JWT rechazado: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            // Subject no es UUID, o claim con tipo inesperado.
            log.warn("JWT con datos inválidos: {}", e.getMessage());
            throw new JwtException("Token inválido: " + e.getMessage());
        }
    }

    // Acepta hospitalId tanto como Integer (caso común) como Long (cuando el ID
    // excede Integer.MAX_VALUE) o cualquier Number. Antes hacíamos
    // claims.get("hospitalId", Integer.class) — JJWT lanzaba RequiredTypeException
    // si Jackson había deserializado el número como Long, y el token válido era
    // rechazado sin razón clara. Esta variante tolerante elimina ese falso negativo.
    private Integer extractHospitalId(Claims claims) {
        Object raw = claims.get("hospitalId");
        if (raw == null) return null;
        if (raw instanceof Number n) return n.intValue();
        log.warn("Claim hospitalId con tipo inesperado: {}", raw.getClass());
        return null;
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
