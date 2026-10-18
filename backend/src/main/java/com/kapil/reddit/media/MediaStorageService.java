package com.kapil.reddit.media;

import com.kapil.reddit.common.exception.BusinessException;
import com.kapil.reddit.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploaded post images on local disk (a Docker volume in compose) and serves them back.
 *
 * <p>Security: the file type is decided from the file's magic bytes, never from the client's
 * Content-Type or filename; only PNG / JPEG / GIF / WEBP are accepted (no SVG, which could carry
 * script). Files are stored under a random UUID name, and served names are matched against a
 * strict pattern, so there is no path traversal.
 */
@Service
@Slf4j
public class MediaStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Pattern STORED_NAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|gif|webp)$");

    private final Path root;

    public MediaStorageService(@Value("${app.media.dir:uploads}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Media storage directory: {}", root);
    }

    /** Saves an uploaded image and returns its public URL path ({@code /media/<name>}). */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("Image is larger than 5 MB");
        }
        try {
            byte[] head = new byte[12];
            int n;
            try (InputStream in = file.getInputStream()) {
                n = in.readNBytes(head, 0, head.length);
            }
            String ext = detectExtension(Arrays.copyOf(head, n));
            if (ext == null) {
                throw new BusinessException("Only PNG, JPEG, GIF and WEBP images are allowed");
            }
            String name = UUID.randomUUID() + "." + ext;
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return "/media/" + name;
        } catch (IOException e) {
            throw new IllegalStateException("Could not store image", e);
        }
    }

    public Resource load(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) {
            throw new ResourceNotFoundException("Media not found");
        }
        Path path = root.resolve(name);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Media not found");
        }
        return new FileSystemResource(path);
    }

    public static MediaType mediaTypeFor(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1);
        return switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            default -> MediaType.parseMediaType("image/webp");
        };
    }

    /** Returns the file extension for a supported image signature, or null. */
    static String detectExtension(byte[] h) {
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') return "gif";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        return null;
    }
}
