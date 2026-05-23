package arsw.asclepio.talk.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// Expone S3Client (operaciones síncronas: put, delete) y S3Presigner (firma
// de URLs GET sin roundtrip HTTP).
//
// Con MinIO necesitábamos DOS beans MinioClient porque MinioClient solo
// soporta un endpoint: uno para subidas (red docker interna) y otro para
// firmar URLs que abre el browser. Con AWS S3 eso desaparece — un único
// endpoint regional sirve ambos propósitos.
//
// En SDK v2, S3Presigner es clase separada de S3Client por diseño: firmar
// es una operación criptográfica local (HMAC-SHA256), sin TCP — separarlos
// evita que el presigner inicialice el pool de conexiones HTTP innecesariamente.
// Ambos implementan Closeable; Spring gestiona su ciclo de vida vía @Bean.
//
// Las cuentas de AWS Academy emiten credenciales STS temporales con tres
// componentes: accessKey + secretKey + sessionToken. Si sessionToken está
// presente usamos AwsSessionCredentials; si no, AwsBasicCredentials.
// Esto permite que el mismo código funcione con IAM permanente o con Learner Lab.
@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final S3Properties props;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials()))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials()))
                .build();
    }

    // Si hay sessionToken construimos AwsSessionCredentials (STS / Learner Lab).
    // Si no, AwsBasicCredentials para IAM permanente con acceso directo.
    private AwsCredentials credentials() {
        String token = props.sessionToken();
        if (token != null && !token.isBlank()) {
            return AwsSessionCredentials.create(props.accessKey(), props.secretKey(), token);
        }
        return AwsBasicCredentials.create(props.accessKey(), props.secretKey());
    }
}
// Daniel Useche
