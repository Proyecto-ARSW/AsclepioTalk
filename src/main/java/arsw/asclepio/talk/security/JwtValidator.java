package arsw.asclepio.talk.security;

import arsw.asclepio.talk.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

// Valida el JWT emitido por AsclepioM1 usando el secreto compartido
// Daniel Useche
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

            return new UserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("rol", String.class),
                    claims.get("nombre", String.class),
                    claims.get("apellido", String.class),
                    claims.get("hospitalId", Integer.class)
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Token inválido o expirado: " + e.getMessage());
        }
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
