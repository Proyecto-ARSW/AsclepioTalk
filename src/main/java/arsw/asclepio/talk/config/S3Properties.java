package arsw.asclepio.talk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Propiedades de AWS S3 para el storage de adjuntos del chat.
//
// A diferencia de MinIO, S3 no requiere distinguir endpoint "interno" vs
// "público": las presigned URLs son HTTPS contra aws.amazon.com, accesibles
// desde cualquier browser sin configuración extra. Una región es suficiente.
//
// El bucket se configura como el alias del access point porque el access point
// define la política de red (origen Internet) — el SDK v2 acepta el alias
// directamente como nombre de bucket; AWS resuelve el routing internamente.
//
// sessionToken: requerido por las credenciales temporales STS que emiten las
// cuentas de AWS Academy / Learner Lab. Con IAM permanente este campo queda
// vacío y S3Config usa AwsBasicCredentials en su lugar.
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
        String region,
        String accessKey,
        String secretKey,
        String sessionToken,
        String bucket,
        int presignedTtlSeconds
) {
}
// Daniel Useche
