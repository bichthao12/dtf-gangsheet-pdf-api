package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.dto.ImageAsset;
import com.example.dtfgangsheet.exception.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class AssetStorageService {

    private static final Logger log = LoggerFactory.getLogger(AssetStorageService.class);

    private final long maxImageBytes;
    private final int httpMaxAttempts;
    private final long retryDelayMs;
    private final long maxRetryDelayMs;
    private final long maxInputRasterPixels;
    private final String imageTempDir;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final String userAgent;
    private final String ffmpegPath;
    private final String ffprobePath;

    public AssetStorageService(ImageProperties imageProps) {
        this.maxImageBytes = imageProps.maxBytes();
        this.httpMaxAttempts = imageProps.httpMaxAttempts();
        this.retryDelayMs = imageProps.retryDelayMs();
        this.maxRetryDelayMs = imageProps.maxRetryDelayMs();
        this.maxInputRasterPixels = imageProps.maxInputRasterPixels();
        this.imageTempDir = imageProps.tempDir();
        this.connectTimeoutMs = imageProps.connectTimeoutMs();
        this.readTimeoutMs = imageProps.readTimeoutMs();
        this.userAgent = imageProps.userAgent();
        this.ffmpegPath  = imageProps.ffmpegPath();
        this.ffprobePath = imageProps.ffprobePath();
    }

    public ImageAsset loadAsset(String img) throws ImageLoadException {
        try {
            if (img == null || img.isBlank()) {
                throw new ImageLoadException("Image path/url must not be blank");
            }
            LoadedFile loadedFile = isHttpUrl(img)
                    ? loadFileFromUrl(img)
                    : loadFileFromLocalPath(img);

            ImageAsset.ImageFormat format = detectFormat(loadedFile.path(), img);
            ImageSize imageSize = readImageSize(loadedFile.path(), img, format);
            validateInputRasterPixels(imageSize, img);

            return new ImageAsset(
                    img,
                    loadedFile.path(),
                    loadedFile.sizeBytes(),
                    imageSize.width(),
                    imageSize.height(),
                    format,
                    loadedFile.temporary()
            );
        } catch (IOException e) {
            throw new ImageLoadException("Cannot load image: " + img, e);
        }
    }

    @PostConstruct
    private void validateFfmpeg() {
        checkBinary(ffmpegPath,  "ffmpeg");
        checkBinary(ffprobePath, "ffprobe");
    }

    private void checkBinary(String path, String name) {
        try {
            new ProcessBuilder(path, "-version")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(5, TimeUnit.SECONDS);
            log.info("{} OK: path={}", name, path);
        } catch (Exception e) {
            log.error("{} not found: path={} — AVIF will fail at runtime", name, path);
        }
    }

    private LoadedFile loadFileFromLocalPath(String img) throws IOException {
        Path path = Path.of(img).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            throw new ImageNotFoundException("Image file does not exist: " + img);
        }

        if (!Files.isRegularFile(path)) {
            throw new ImageNotFoundException("Image path is not a file: " + img);
        }

        long sizeBytes = Files.size(path);
        validateImageSize(sizeBytes, img);

        return new LoadedFile(path, sizeBytes, false);
    }

    private void validateInputRasterPixels(ImageSize imageSize, String source) {
        if (imageSize == null) {
            throw new ImageLoadException("Image size metadata is missing");
        }
        if (imageSize.width() <= 0 || imageSize.height() <= 0) {
            throw new ImageLoadException(
                    "Invalid image dimensions. width=" + imageSize.width()
                            + ", height=" + imageSize.height()
            );
        }

        long pixels = (long) imageSize.width() * imageSize.height();

        if (pixels > maxInputRasterPixels) {
            throw new ImageSizeExceededException(
                    "Image raster pixels exceed limit. pixels=" + pixels
                            + ", maxPixels=" + maxInputRasterPixels + ", source=" + source);
        }
    }

    private LoadedFile loadFileFromUrl(String imageUrl) throws IOException {
        Exception exception = null;
        int maxAttempts = Math.max(1, httpMaxAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpURLConnection httpConnection = null;
            Path tempFile = null;

            try {
                URLConnection connection = URI.create(imageUrl).toURL().openConnection();
                connection.setConnectTimeout(connectTimeoutMs);
                connection.setReadTimeout(readTimeoutMs);
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept", "image/*");

                if (connection instanceof HttpURLConnection detectedHttpConnection) {
                    httpConnection = detectedHttpConnection;
                    httpConnection.setInstanceFollowRedirects(true);
                    int statusCode = httpConnection.getResponseCode();

                    if (HttpStatus.Series.resolve(statusCode) != HttpStatus.Series.SUCCESSFUL) {
                        if (statusCode == HttpStatus.NOT_FOUND.value()) {
                            throw new ImageNotFoundException("Image not found at URL: " + imageUrl);
                        }
                        if (shouldRetry(statusCode)) {
                            if (attempt < maxAttempts) {
                                exception = new ImageLoadException(
                                        "Cannot load image from URL. status=" + statusCode
                                                + ", attempts=" + attempt + ", url=" + imageUrl);
                                sleepBeforeRetry(httpConnection, attempt);
                                continue;
                            }
                            throw new TransientImageLoadException(
                                    "Cannot load image from URL. status=" + statusCode
                                            + ", attempts=" + attempt + ", url=" + imageUrl);
                        }
                        throw new ImageLoadException(
                                "Cannot load image from URL. status=" + statusCode
                                        + ", attempts=" + attempt + ", url=" + imageUrl);
                    }
                    String contentType = httpConnection.getContentType();
                    if (contentType != null && !isSupportedImageContentType(contentType)) {
                        throw new UnsupportedImageFormatException(
                                "Unsupported image format. contentType=" + contentType + ", url=" + imageUrl);
                    }
                }

                validateImageSize(connection.getContentLengthLong(), imageUrl);

                tempFile = createTempFile(".img");
                long totalBytesRead;

                try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                     OutputStream outputStream = Files.newOutputStream(tempFile)) {
                    totalBytesRead = copyWithMaxSize(inputStream, outputStream, imageUrl);
                }
                return new LoadedFile(tempFile, totalBytesRead, true);

            } catch (ImageLoadException ex) {
                deleteTempFileQuietly(tempFile);
                throw ex;

            } catch (IllegalArgumentException ex) {
                deleteTempFileQuietly(tempFile);
                throw new ImageLoadException("Invalid image URL: " + imageUrl, ex);

            } catch (IOException ex) {
                deleteTempFileQuietly(tempFile);
                exception = ex;

                if (attempt < maxAttempts) {
                    sleepBeforeRetry(null, attempt);
                    continue;
                }

                throw new TransientImageLoadException("Cannot load image from URL: " + imageUrl, ex);
            } finally {
                if (httpConnection != null) {
                    httpConnection.disconnect();
                }
            }
        }

        throw new TransientImageLoadException("Cannot load image from URL after " + maxAttempts + " attempt(s): " + imageUrl, exception);
    }

    private boolean isSupportedImageContentType(String contentType) {
        String lower = contentType.toLowerCase();
        return lower.startsWith("image/png")
                || lower.startsWith("image/webp")
                || lower.startsWith("image/avif")   // thêm
                || lower.startsWith("image/gif");
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            log.warn("Failed to delete temp file, may accumulate on disk: path={}", tempFile);
        }
    }

    private long copyWithMaxSize(InputStream inputStream, OutputStream outputStream, String source) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int bytesRead;
        long totalBytesRead = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            totalBytesRead += bytesRead;

            if (totalBytesRead > maxImageBytes) {
                throw new ImageSizeExceededException(
                        "Image is too large. maxBytes=" + maxImageBytes + ", source=" + source);
            }

            outputStream.write(buffer, 0, bytesRead);
        }

        return totalBytesRead;
    }

    /**
     * Returns the file size in bytes if cheaply available (local files only);
     * -1 for HTTP URLs or paths that are not regular files. Used for pre-flight
     * total-size budgeting without performing a download or read.
     */
    public long peekLocalSize(String img) {
        if (img == null || img.isBlank() || isHttpUrl(img)) return -1L;
        Path path = Path.of(img).normalize();
        if (!Files.isRegularFile(path)) return -1L;
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1L;
        }
    }

    /**
     * Decodes an image at a target render size to limit memory usage.
     */
    public BufferedImage decodeForRender(
            ImageAsset imageAsset,
            int targetWidth,
            int targetHeight
    ) throws IOException {
        if (imageAsset.format() == ImageAsset.ImageFormat.AVIF) {
            return decodeAvifWithFfmpeg(imageAsset, targetWidth, targetHeight);
        }
        BufferedImage decodedImage = readImage(imageAsset.path(), imageAsset.source());
        return scaleDownIfNeeded(decodedImage, targetWidth, targetHeight);
    }

    private BufferedImage decodeAvifWithFfmpeg(
            ImageAsset imageAsset,
            int targetWidth,
            int targetHeight
    ) throws IOException {
        Path pngTemp = createTempFile(".png");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-i", imageAsset.path().toString(),
                    "-vframes", "1",
                    "-vf", "scale=" + targetWidth + ":" + targetHeight,
                    "-vcodec", "png",
                    pngTemp.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output;
            try {
                output = new String(process.getInputStream().readAllBytes()).trim();
                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new ImageLoadException(
                            "ffmpeg timed out decoding AVIF: " + imageAsset.source());
                }
                if (process.exitValue() != 0) {
                    log.debug("ffmpeg output: {}", output);
                    throw new ImageLoadException(
                            "ffmpeg failed. exitCode=" + process.exitValue()
                                    + ", output=" + output
                                    + ", source=" + imageAsset.source());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ImageLoadException(
                        "Interrupted decoding AVIF: " + imageAsset.source(), e);
            }

            BufferedImage decoded = ImageIO.read(pngTemp.toFile());
            if (decoded == null) {
                throw new ImageLoadException(
                        "Cannot read ffmpeg output: " + imageAsset.source());
            }
            return scaleDownIfNeeded(decoded, targetWidth, targetHeight);

        } finally {
            deleteTempFileQuietly(pngTemp);
        }
    }

    private Path createTempFile(String suffix) throws IOException {
        Path tempDir = Path.of(imageTempDir).toAbsolutePath().normalize();
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }
        return Files.createTempFile(tempDir, "gangsheet-image-", suffix);
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == HttpStatus.TOO_MANY_REQUESTS.value()
                || statusCode == HttpURLConnection.HTTP_BAD_GATEWAY
                || statusCode == HttpURLConnection.HTTP_UNAVAILABLE
                || statusCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
    }

    private void sleepBeforeRetry(HttpURLConnection connection, int attempt) throws ImageLoadException {
        long delayMs = resolveRetryDelayMs(connection, attempt);

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ImageLoadException("Interrupted while retrying image download", ex);
        }
    }

    private long resolveRetryDelayMs(HttpURLConnection connection, int attempt) {
        if (connection != null) {
            String retryAfter = connection.getHeaderField("Retry-After");

            if (retryAfter != null && !retryAfter.isBlank()) {
                try {
                    long retryAfterMs = Long.parseLong(retryAfter.trim()) * 1_000L;
                    return Math.max(0, Math.min(retryAfterMs, maxRetryDelayMs));
                } catch (NumberFormatException ignored) {
                    // Fall through to local backoff when Retry-After is an HTTP date.
                }
            }
        }

        long exponentialDelayMs = retryDelayMs * (1L << Math.max(0, attempt - 1));
        return Math.max(0, Math.min(exponentialDelayMs, maxRetryDelayMs));
    }

    private void validateImageSize(long imageSizeBytes, String source) {
        if (imageSizeBytes > maxImageBytes) {
            throw new ImageSizeExceededException(
                    "Image is too large. sizeBytes=" + imageSizeBytes
                            + ", maxBytes=" + maxImageBytes + ", source=" + source);
        }
    }

    private ImageAsset.ImageFormat detectFormat(Path path, String source) throws IOException {
        byte[] header = new byte[64];

        try (InputStream inputStream = Files.newInputStream(path)) {
            int read = inputStream.readNBytes(header, 0, header.length);

            if (read >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                throw new UnsupportedImageFormatException(
                        "JPEG images are not supported: " + source
                );
            }

            if (read >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A) {
                return ImageAsset.ImageFormat.PNG;
            }

            if (read >= 6
                    && header[0] == 'G'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == '8') {
                return ImageAsset.ImageFormat.GIF;
            }

            if (read >= 12
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == 'F'
                    && header[8] == 'W'
                    && header[9] == 'E'
                    && header[10] == 'B'
                    && header[11] == 'P') {
                return ImageAsset.ImageFormat.WEBP;
            }
            // AVIF: ftyp box tại offset 4, brand "avif" hoặc "avis" tại offset 8
            if (isAvif(header, read)) {
                return ImageAsset.ImageFormat.AVIF;
            }
        }

        return ImageAsset.ImageFormat.OTHER;
    }

    private boolean isAvif(byte[] header, int read) {
        int ftypOffset = -1;
        for (int i = 0; i <= read - 4; i++) {
            if (header[i]     == 'f' && header[i + 1] == 't'
                    && header[i + 2] == 'y' && header[i + 3] == 'p') {
                ftypOffset = i;
                break;
            }
        }
        if (ftypOffset < 0) return false;

        for (int i = ftypOffset; i <= read - 4; i += 4) {
            if (header[i]     == 'a' && header[i + 1] == 'v'
                    && header[i + 2] == 'i'
                    && (header[i + 3] == 'f' || header[i + 3] == 's')) {
                return true;
            }
        }
        return false;
    }

    private String bytesToHex(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString().trim();
    }

    private ImageSize readImageSize(Path path, String source, ImageAsset.ImageFormat format) throws IOException {
        // AVIF dùng libvips
        if (format == ImageAsset.ImageFormat.AVIF) {
            return readAvifImageSize(path, source);
        }

        // PNG/WEBP/GIF dùng ImageIO
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(path.toFile())) {
            if (imageInputStream == null) {
                throw new ImageLoadException("Cannot read image format: " + source);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new UnsupportedImageFormatException("Unsupported image format: " + source);
            }
            ImageReader reader = readers.next();
            try {
                imageInputStream.seek(0);
                reader.setInput(imageInputStream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new ImageLoadException("Invalid image dimensions: " + source);
                }
                return new ImageSize(width, height);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new ImageLoadException("Cannot read image dimensions: " + source, e);
            } finally {
                reader.dispose();
            }
        }
    }

    private ImageSize readAvifImageSize(Path path, String source) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                ffprobePath,
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height",
                "-of", "csv=p=0",
                path.toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes()).trim();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ImageLoadException("ffprobe timed out: " + source);
            }
            if (process.exitValue() != 0) {
                throw new ImageLoadException(
                        "ffprobe failed. exitCode=" + process.exitValue()
                                + ", output=" + output + ", source=" + source);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageLoadException("Interrupted reading AVIF size: " + source, e);
        }

        String[] parts = output.split(",");
        if (parts.length < 2) {
            throw new ImageLoadException(
                    "Cannot parse AVIF dimensions. output='"
                            + output + "', source=" + source);
        }
        try {
            int width  = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width <= 0 || height <= 0) {
                throw new ImageLoadException(
                        "Invalid AVIF dimensions. width=" + width
                                + ", height=" + height + ", source=" + source);
            }
            return new ImageSize(width, height);
        } catch (NumberFormatException e) {
            throw new ImageLoadException(
                    "Cannot parse AVIF dimensions. output='"
                            + output + "', source=" + source, e);
        }
    }

    private BufferedImage readImage(Path path, String source) throws IOException {
        try (ImageInputStream imageInputStream = createImageInputStream(path, source)) {
            ImageReader imageReader = getImageReader(imageInputStream, source);

            try {
                imageInputStream.seek(0);
                imageReader.setInput(imageInputStream, true, true);

                ImageReadParam readParam = imageReader.getDefaultReadParam();

                BufferedImage image;
                try {
                    image = imageReader.read(0, readParam);
                    if (image == null) {
                        throw new ImageLoadException("Cannot decode image: " + source);
                    }
                    return image;

                } catch (IOException | RuntimeException e) {
                    throw new ImageLoadException("Cannot decode image: " + source, e);
                }
            } finally {
                imageReader.dispose();
            }
        }
    }

    private ImageInputStream createImageInputStream(Path path, String source) throws IOException {
        ImageInputStream imageInputStream = ImageIO.createImageInputStream(path.toFile());

        if (imageInputStream == null) {
            throw new ImageLoadException("Cannot open image stream: " + source);
        }

        return imageInputStream;
    }

    private ImageReader getImageReader(ImageInputStream imageInputStream, String source) {
        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);

        if (!imageReaders.hasNext()) {
            throw new UnsupportedImageFormatException("Unsupported image format: " + source);
        }

        return imageReaders.next();
    }

    private BufferedImage scaleDownIfNeeded(
            BufferedImage image,
            int targetWidth,
            int targetHeight
    ) {
        if (image.getWidth() <= targetWidth && image.getHeight() <= targetHeight) {
            return image;
        }

        double scale = Math.min(
                (double) targetWidth / image.getWidth(),
                (double) targetHeight / image.getHeight()
        );

        if (scale >= 1.0) {
            return image;
        }

        int scaledWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int imageType = image.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;

        BufferedImage scaledImage = new BufferedImage(scaledWidth, scaledHeight, imageType);
        Graphics2D graphics = scaledImage.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );
            graphics.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
        } finally {
            graphics.dispose();
        }

        return scaledImage;
    }

    private boolean isHttpUrl(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private record ImageSize(int width, int height) {
    }

    public void cleanupTempAssets(List<ImageAsset> imageAssets) {
        if (imageAssets == null || imageAssets.isEmpty()) {
            return;
        }

        Set<Path> deletedPaths = new HashSet<>();

        for (ImageAsset imageAsset : imageAssets) {
            if (imageAsset != null && imageAsset.temporary() && deletedPaths.add(imageAsset.path())) {
                try {
                    Files.deleteIfExists(imageAsset.path());
                } catch (IOException ignored) {
                    log.warn("Failed to cleanup temp asset, may accumulate on disk: path={}", imageAsset.path());
                }
            }
        }
    }

    private record LoadedFile(
            Path path,
            long sizeBytes,
            boolean temporary
    ) {
    }
}
