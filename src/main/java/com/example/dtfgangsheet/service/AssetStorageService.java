package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.config.ImageProperties;
import com.example.dtfgangsheet.dto.ImageAsset;
import com.example.dtfgangsheet.dto.LoadedFile;
import com.example.dtfgangsheet.exception.ImageLoadException;
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

@Service
public class AssetStorageService {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    /**
     * Number of leading bytes probed when sniffing the JPEG SOF marker. Large enough
     * to skip typical APPn/EXIF/ICC segments while staying far below full-image size.
     */
    private static final int JPEG_HEADER_PROBE_BYTES = 64 * 1024;
    private static final String IMAGE_USER_AGENT = "DtfGangsheet/1.0 (+https://your-domain.example)";

    private final long maxImageBytes;
    private final int httpMaxAttempts;
    private final long retryDelayMs;
    private final long maxRetryDelayMs;
    private final long maxInputRasterPixels;
    private final String imageTempDir;

    public AssetStorageService(ImageProperties imageProps) {
        this.maxImageBytes = imageProps.maxBytes();
        this.httpMaxAttempts = imageProps.httpMaxAttempts();
        this.retryDelayMs = imageProps.retryDelayMs();
        this.maxRetryDelayMs = imageProps.maxRetryDelayMs();
        this.maxInputRasterPixels = imageProps.maxInputRasterPixels();
        this.imageTempDir = imageProps.tempDir();
    }

    public ImageAsset loadAsset(String img) throws ImageLoadException {
        try {
            if (img == null || img.isBlank()) {
                throw new ImageLoadException("Image path/url must not be blank");
            }
            LoadedFile loadedFile = isHttpUrl(img)
                    ? loadFileFromUrl(img)
                    : loadFileFromLocalPath(img);

            ImageAsset.ImageFormat format = detectFormat(loadedFile.path());
            ImageSize imageSize = readImageSize(loadedFile.path(), img);
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

    private LoadedFile loadFileFromLocalPath(String img) throws IOException {
        Path path = Path.of(img).toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            throw new ImageLoadException("Image file does not exist: " + img);
        }

        if (!Files.isRegularFile(path)) {
            throw new ImageLoadException("Image path is not a file: " + img);
        }

        long sizeBytes = Files.size(path);
        validateImageSize(sizeBytes, img);

        return new LoadedFile(path, sizeBytes, false);
    }

    private void validateInputRasterPixels(ImageSize imageSize, String source) throws ImageLoadException {
        long pixels = (long) imageSize.width() * imageSize.height();

        if (pixels > maxInputRasterPixels) {
            throw new ImageLoadException(
                    "Image raster pixels exceed limit. pixels="
                            + pixels
                            + ", maxPixels="
                            + maxInputRasterPixels
                            + ", source="
                            + source
            );
        }
    }

    private LoadedFile loadFileFromUrl(String imageUrl) throws IOException {
        IOException lastException = null;
        int maxAttempts = Math.max(1, httpMaxAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpURLConnection httpConnection = null;
            Path tempFile = null;

            try {
                URLConnection connection = URI.create(imageUrl).toURL().openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", IMAGE_USER_AGENT);
                connection.setRequestProperty("Accept", "image/*");

                if (connection instanceof HttpURLConnection detectedHttpConnection) {
                    httpConnection = detectedHttpConnection;
                    httpConnection.setInstanceFollowRedirects(true);
                    int statusCode = httpConnection.getResponseCode();

                    if (statusCode < 200 || statusCode >= 300) {
                        ImageLoadException exception = new ImageLoadException(
                                "Cannot load image from URL. status="
                                        + statusCode
                                        + ", attempts="
                                        + attempt
                                        + ", url="
                                        + imageUrl
                        );

                        if (shouldRetry(statusCode) && attempt < maxAttempts) {
                            lastException = exception;
                            sleepBeforeRetry(httpConnection, attempt);
                            continue;
                        }

                        throw exception;
                    }
                }

                validateImageSize(connection.getContentLengthLong(), imageUrl);

                tempFile = createTempImageFile();
                long totalBytesRead;

                try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                     OutputStream outputStream = Files.newOutputStream(tempFile)) {
                    totalBytesRead = copyWithMaxSize(inputStream, outputStream, imageUrl);
                }

                return new LoadedFile(tempFile, totalBytesRead, true);
            } catch (ImageLoadException ex) {
                deleteTempFileQuietly(tempFile);
                throw ex;
            } catch (IOException ex) {
                deleteTempFileQuietly(tempFile);
                lastException = ex;

                if (attempt < maxAttempts) {
                    sleepBeforeRetry(null, attempt);
                    continue;
                }

                throw new ImageLoadException("Cannot load image from URL: " + imageUrl, ex);
            } finally {
                if (httpConnection != null) {
                    httpConnection.disconnect();
                }
            }
        }

        throw new ImageLoadException("Cannot load image from URL after retries: " + imageUrl, lastException);
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // Best-effort cleanup. Do not override the original exception.
        }
    }

    private Path createTempImageFile() throws IOException {
        Path tempDir = Path.of(imageTempDir).toAbsolutePath().normalize();

        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        return Files.createTempFile(tempDir, "gangsheet-image-", ".img");
    }

    private long copyWithMaxSize(InputStream inputStream, OutputStream outputStream, String source) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int bytesRead;
        long totalBytesRead = 0;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            totalBytesRead += bytesRead;

            if (totalBytesRead > maxImageBytes) {
                throw new ImageLoadException(
                        "Image is too large. maxBytes="
                                + maxImageBytes
                                + ", source="
                                + source
                );
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
    public long peekLocalSize(String img) throws IOException {
        if (img == null || img.isBlank() || isHttpUrl(img)) {
            return -1L;
        }

        Path path = Path.of(img).normalize();
        if (!Files.isRegularFile(path)) {
            return -1L;
        }

        return Files.size(path);
    }

    public BufferedImage decodeForRender(
            ImageAsset imageAsset,
            int targetWidth,
            int targetHeight
    ) throws IOException {
        BufferedImage decodedImage = readImage(
                imageAsset.path(),
                imageAsset.source(),
                calculateSubsampling(
                        imageAsset.width(),
                        imageAsset.height(),
                        targetWidth,
                        targetHeight
                )
        );

        return scaleDownIfNeeded(decodedImage, targetWidth, targetHeight);
    }

    private boolean shouldRetry(int statusCode) {
        return statusCode == 429
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

    private void validateImageSize(long imageSizeBytes, String source) throws ImageLoadException {
        if (imageSizeBytes > maxImageBytes) {
            throw new ImageLoadException(
                    "Image is too large. sizeBytes="
                            + imageSizeBytes
                            + ", maxBytes="
                            + maxImageBytes
                            + ", source="
                            + source
            );
        }
    }

    private ImageAsset.ImageFormat detectFormat(Path path) throws IOException {
        byte[] header = new byte[3];

        try (InputStream inputStream = Files.newInputStream(path)) {
            int read = inputStream.read(header, 0, 3);

            if (read >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                return ImageAsset.ImageFormat.JPEG;
            }
        }

        return ImageAsset.ImageFormat.OTHER;
    }

    private ImageSize readImageSize(Path path, String source) throws IOException {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(path.toFile())) {
            if (imageInputStream == null) {
                throw new ImageLoadException("Cannot create image input stream: " + source);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);

            if (!readers.hasNext()) {
                throw new ImageLoadException("Unsupported image format: " + source);
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInputStream, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0) {
                    throw new ImageLoadException("Invalid image size: " + source);
                }

                return new ImageSize(width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Reads the JPEG SOF marker's Nf (number of components) field from the start of
     * the file. Only the header is read (the SOF marker appears before the scan data),
     * so we avoid loading the whole image into memory. Best-effort: returns -1 on any
     * read failure or when the SOF marker is not found within the probed prefix.
     */
    public int tryReadJpegComponents(Path path) throws IOException {
        byte[] header = new byte[JPEG_HEADER_PROBE_BYTES];
        int read;

        try (InputStream inputStream = Files.newInputStream(path)) {
            read = inputStream.readNBytes(header, 0, header.length);
        }

        if (read < header.length) {
            header = java.util.Arrays.copyOf(header, read);
        }

        return tryReadJpegComponents(header);
    }

    /**
     * Reads the JPEG SOF marker's Nf (number of components) field directly from bytes.
     * Returns 1=Gray, 3=RGB/YCbCr, 4=CMYK/YCCK, -1 if not JPEG or malformed.
     */
    private int tryReadJpegComponents(byte[] bytes) {
        if (bytes == null
                || bytes.length < 4
                || (bytes[0] & 0xFF) != 0xFF
                || (bytes[1] & 0xFF) != 0xD8) {
            return -1;
        }

        int i = 2;
        while (i + 4 <= bytes.length) {
            while (i < bytes.length && (bytes[i] & 0xFF) == 0xFF) {
                i++;
            }
            if (i >= bytes.length) {
                return -1;
            }

            int marker = bytes[i] & 0xFF;
            i++;

            if (marker == 0x00 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD9)) {
                continue;
            }

            if (i + 2 > bytes.length) {
                return -1;
            }

            int segmentLength = ((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF);
            if (segmentLength < 2) {
                return -1;
            }

            boolean isStartOfFrame = marker >= 0xC0 && marker <= 0xCF
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;

            if (isStartOfFrame) {
                if (i + 8 > bytes.length) {
                    return -1;
                }
                return bytes[i + 7] & 0xFF;
            }

            i += segmentLength;
        }
        return -1;
    }

    private BufferedImage readImage(Path path, String source, int subsampling) throws IOException {
        try (ImageInputStream imageInputStream = createImageInputStream(path, source)) {
            ImageReader imageReader = getImageReader(imageInputStream, source);

            try {
                imageInputStream.seek(0);
                imageReader.setInput(imageInputStream, true, true);

                ImageReadParam readParam = imageReader.getDefaultReadParam();

                if (subsampling > 1) {
                    readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }

                BufferedImage image = imageReader.read(0, readParam);

                if (image == null) {
                    throw new ImageLoadException("Cannot decode image: " + source);
                }

                return image;
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

    private ImageReader getImageReader(ImageInputStream imageInputStream, String source) throws IOException {
        Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);

        if (!imageReaders.hasNext()) {
            throw new ImageLoadException("Unsupported image format: " + source);
        }

        return imageReaders.next();
    }

    private int calculateSubsampling(
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        int safeTargetWidth = Math.max(1, targetWidth);
        int safeTargetHeight = Math.max(1, targetHeight);

        return Math.max(
                1,
                Math.min(
                        sourceWidth / safeTargetWidth,
                        sourceHeight / safeTargetHeight
                )
        );
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

    private record ImageSize(int width, int height) { }
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
                    // Best-effort cleanup.
                }
            }
        }
    }
}
