package arsw.asclepio.talk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Propiedades de MinIO (S3-compatible) para el storage de adjuntos del chat.
// El backend usa `endpoint` para hablar con el servidor (red interna docker),
// pero firma las URLs presignadas contra `publicEndpoint` para que el browser
// — que vive en el host — pueda abrirlas via el puerto publicado.
// Daniel Useche
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        int presignedTtlSeconds
) {
}
