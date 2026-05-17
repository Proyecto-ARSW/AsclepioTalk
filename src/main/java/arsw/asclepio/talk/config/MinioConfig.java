package arsw.asclepio.talk.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Expone dos MinioClient: uno apuntando al endpoint interno (red docker)
// para uploads/deletes, y otro al endpoint publico para firmar URLs que
// abrira el browser. MinioClient solo soporta UN endpoint por instancia,
// asi que dos beans separados es la forma mas limpia de tener ambos.
//
// IMPORTANTE: la region se configura explicita ("us-east-1", default S3).
// Sin esto, MinioClient hace una llamada HTTP de descubrimiento al endpoint
// la primera vez que se firma una URL — y eso falla para el cliente publico
// porque desde dentro del container `localhost:9000` no resuelve a MinIO.
// Daniel Useche
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private static final String REGION = "us-east-1";

    private final MinioProperties props;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(props.endpoint())
                .region(REGION)
                .credentials(props.accessKey(), props.secretKey())
                .build();
    }

    @Bean(name = "minioPublicClient")
    public MinioClient minioPublicClient() {
        return MinioClient.builder()
                .endpoint(props.publicEndpoint())
                .region(REGION)
                .credentials(props.accessKey(), props.secretKey())
                .build();
    }
}
