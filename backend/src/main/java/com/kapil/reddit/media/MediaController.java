package com.kapil.reddit.media;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

/**
 * Image upload for posts. The client uploads first, then sends the returned URL in
 * {@code CreatePostRequest.media[].mediaUrl}.
 */
@RestController
@RequiredArgsConstructor
public class MediaController {

    private final MediaStorageService storage;

    /** Authenticated (see SecurityConfig). Multipart field name: {@code file}. */
    @PostMapping("/api/media")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        return Map.of("url", storage.store(file), "mediaType", "IMAGE");
    }

    /** Public: images are shown in public feeds. Names are random UUIDs, so they never change. */
    @GetMapping("/media/{name}")
    public ResponseEntity<Resource> serve(@PathVariable String name) {
        Resource file = storage.load(name);
        return ResponseEntity.ok()
                .contentType(MediaStorageService.mediaTypeFor(name))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .body(file);
    }
}
