package arsw.asclepio.talk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
// Daniel Useche
public record JwtProperties(String secret) {
}
