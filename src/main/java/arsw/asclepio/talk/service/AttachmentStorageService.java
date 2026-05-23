package arsw.asclepio.talk.service;

import arsw.asclepio.talk.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

// Subida, firma de URL y borrado de adjuntos en AWS S3.
//
// La lógica de validación (MIME, tamaño) y sanitización no cambia respecto
// a la versión MinIO — solo cambia la capa de transporte debajo.
//
// Diferencias clave SDK v2 vs MinIO SDK:
//  - upload(): RequestBody.fromInputStream(stream, size) requiere contentLength
//    explícito porque HTTP/1.1 necesita Content-Length antes del body.
//    MultipartFile.getSize() lo provee sin releer el stream.
//  - presignedGetUrl(): GetObjectPresignRequest envuelve un GetObjectRequest y
//    le añade el TTL. El presigner firma localmente (sin roundtrip a AWS) y
//    devuelve URL HTTPS válida para cualquier cliente durante el TTL.
//  - delete(): DeleteObjectRequest — misma semántica best-effort que antes.
//  - @RequiredArgsConstructor funciona porque S3Client y S3Presigner son tipos
//    distintos: Spring los resuelve por tipo sin necesidad de @Qualifier.
//
// Daniel Useche
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentStorageService {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    public static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "application/pdf"
    );

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties props;

    public record StoredFile(String storageKey, String fileName, String mimeType, long sizeBytes) {}

    public StoredFile upload(UUID conversationId, MultipartFile file) {
        validate(file);

        String safeName = sanitizeFileName(file.getOriginalFilename());
        String ext = extensionOf(safeName);
        String storageKey = "conv/" + conversationId + "/" + UUID.randomUUID() + ext;

        try (InputStream in = file.getInputStream()) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(storageKey)
                    .contentType(file.getContentType())
                    .build();

            // fromInputStream requiere el tamaño porque S3 necesita Content-Length
            // en la petición HTTP; MultipartFile ya lo conoce sin costo extra.
            s3Client.putObject(putRequest, RequestBody.fromInputStream(in, file.getSize()));

        } catch (Exception e) {
            // No exponemos detalles del backend al cliente — el handler global
            // lo traduce a 500. Lo logueamos para diagnóstico.
            log.error("Fallo al subir adjunto a S3 (conv={}, key={})", conversationId, storageKey, e);
            throw new IllegalStateException("No se pudo almacenar el archivo");
        }

        return new StoredFile(storageKey, safeName, file.getContentType(), file.getSize());
    }

    public String presignedGetUrl(String storageKey) {
        try {
            // GetObjectRequest describe QUÉ objeto; GetObjectPresignRequest añade
            // el TTL y le pide al presigner que firme con HMAC-SHA256 localmente
            // — sin roundtrip a AWS. La URL resultante es válida para cualquier
            // cliente HTTP durante el período configurado.
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(storageKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(props.presignedTtlSeconds()))
                    .getObjectRequest(getRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();

        } catch (Exception e) {
            log.error("Fallo al firmar URL presignada (key={})", storageKey, e);
            throw new IllegalStateException("No se pudo generar URL de descarga");
        }
    }

    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(storageKey)
                    .build());
        } catch (Exception e) {
            // Best-effort: si falla el borrado el mensaje ya está soft-deleted;
            // logueamos para limpieza manual o por un job offline futuro.
            log.warn("No se pudo borrar adjunto en S3 (key={}): {}", storageKey, e.getMessage());
        }
    }

    // ─── Internos ──────────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("El archivo supera el límite de 10 MB");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime)) {
            throw new IllegalArgumentException("Tipo de archivo no permitido (JPG, PNG, WEBP, GIF o PDF)");
        }
    }

    // Defensiva contra path traversal y caracteres raros en el filename
    // mostrado al cliente. No se usa como key en el bucket (ese es un UUID).
    private String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "archivo";
        String name = raw.replaceAll("[\\\\/\\x00-\\x1F]", "_").trim();
        if (name.length() > 200) name = name.substring(name.length() - 200);
        return name.isBlank() ? "archivo" : name;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        String ext = fileName.substring(dot).toLowerCase();
        // Solo permitimos extensiones cortas/seguras para la key
        return ext.length() <= 8 ? ext.replaceAll("[^a-z0-9.]", "") : "";
    }

    public boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
