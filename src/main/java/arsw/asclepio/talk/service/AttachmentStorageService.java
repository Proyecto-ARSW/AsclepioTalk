package arsw.asclepio.talk.service;

import arsw.asclepio.talk.config.MinioProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// Subida, firma de URL y borrado de adjuntos en MinIO.
//
// Validamos MIME y tamano antes de tocar la red — el rechazo claro viene
// de aqui con mensaje en espanol para que el frontend lo muestre tal cual.
// El bucket es privado, asi que las descargas requieren URL presignada
// (TTL definido en application.yml — 10 min en dev).
//
// Daniel Useche
@Slf4j
@Service
public class AttachmentStorageService {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    public static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "application/pdf"
    );

    private final MinioClient minioClient;
    private final MinioClient minioPublicClient;
    private final MinioProperties props;

    public AttachmentStorageService(
            MinioClient minioClient,
            @Qualifier("minioPublicClient") MinioClient minioPublicClient,
            MinioProperties props) {
        this.minioClient = minioClient;
        this.minioPublicClient = minioPublicClient;
        this.props = props;
    }

    public record StoredFile(String storageKey, String fileName, String mimeType, long sizeBytes) {}

    public StoredFile upload(UUID conversationId, MultipartFile file) {
        validate(file);

        String safeName = sanitizeFileName(file.getOriginalFilename());
        String ext = extensionOf(safeName);
        String storageKey = "conv/" + conversationId + "/" + UUID.randomUUID() + ext;

        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(storageKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            // No exponemos detalles del backend al cliente — el handler global
            // lo traduce a 500. Lo logueamos para diagnostico.
            log.error("Fallo al subir adjunto a MinIO (conv={}, key={})", conversationId, storageKey, e);
            throw new IllegalStateException("No se pudo almacenar el archivo");
        }

        return new StoredFile(storageKey, safeName, file.getContentType(), file.getSize());
    }

    public String presignedGetUrl(String storageKey) {
        try {
            return minioPublicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(props.bucket())
                    .object(storageKey)
                    .expiry(props.presignedTtlSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("Fallo al firmar URL presignada (key={})", storageKey, e);
            throw new IllegalStateException("No se pudo generar URL de descarga");
        }
    }

    public void delete(String storageKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(storageKey)
                    .build());
        } catch (Exception e) {
            // Best-effort: si falla el borrado el mensaje ya esta soft-deleted;
            // logueamos para limpieza manual o por una job offline futura.
            log.warn("No se pudo borrar adjunto en MinIO (key={}): {}", storageKey, e.getMessage());
        }
    }

    // ─── Internos ──────────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo esta vacio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("El archivo supera el limite de 10 MB");
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
