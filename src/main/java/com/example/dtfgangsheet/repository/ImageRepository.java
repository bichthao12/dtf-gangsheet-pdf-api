package com.example.dtfgangsheet.repository;

import com.example.dtfgangsheet.config.ImageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public class ImageRepository {

    private final Path storageDir;

    public ImageRepository(ImageProperties imageProperties) {
        this.storageDir = Path.of(imageProperties.storageDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(storageDir);
    }

    public boolean exists(String imageId) {
        return findDesignFile(imageId).isPresent();
    }

    public Optional<Long> sizeBytes(String imageId) {
        return findDesignFile(imageId).map(path -> {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return -1L;
            }
        }).filter(size -> size >= 0);
    }

    public Optional<Path> findDesignFile(String imageId) {
        if (imageId == null || imageId.isBlank()) {
            return Optional.empty();
        }

        Path imageDir = storageDir.resolve(imageId).normalize();
        if (!imageDir.startsWith(storageDir) || !Files.isDirectory(imageDir)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(imageDir)) {
            return files.filter(Files::isRegularFile).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
