package com.example.dtfgangsheet.service;

import com.example.dtfgangsheet.dto.ImageAsset;
import com.example.dtfgangsheet.exception.ImageLoadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

@Service
public class AssetStorageService {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    @Value("${app.image.max-bytes:104857600}")
    private long maxImageBytes;

    @Value("${app.image.user-agent:dtf-gangsheet-pdf-api/1.0}")
    private String imageUserAgent;

    @Value("${app.image.http-max-attempts:3}")
    private int httpMaxAttempts;

    @Value("${app.image.retry-delay-ms:1000}")
    private long retryDelayMs;

    @Value("${app.image.max-retry-delay-ms:5000}")
    private long maxRetryDelayMs;

    public ImageAsset loadAsset(String img) throws IOException {
        if (img == null || img.isBlank()) {
            throw new ImageLoadException("Image path/url must not be blank");
        }

        byte[] imageBytes;
        if (isHttpUrl(img)) {
            imageBytes = loadBytesFromUrl(img);
        } else {
            imageBytes = loadBytesFromLocalPath(img);
        }

        ImageAsset.ImageFormat format = detectFormat(imageBytes);
        ImageSize imageSize = readImageSize(imageBytes, img);

        return new ImageAsset(
                img,
                imageBytes,
                imageSize.width(),
                imageSize.height(),
                format
        );
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
                imageAsset.bytes(),
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

    private byte[] loadBytesFromUrl(String imageUrl) throws IOException {
        IOException lastException = null;
        int maxAttempts = Math.max(1, httpMaxAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpURLConnection httpConnection = null;

            try {
                URLConnection connection = URI.create(imageUrl).toURL().openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", imageUserAgent);
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

                try (InputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
                    return readAllBytes(inputStream, imageUrl);
                }
            } catch (ImageLoadException ex) {
                throw ex;
            } catch (IOException ex) {
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

    private byte[] loadBytesFromLocalPath(String imagePath) throws IOException {
        Path path = Path.of(imagePath).normalize();

        if (!Files.isRegularFile(path)) {
            throw new ImageLoadException("Image file not found: " + path.toAbsolutePath());
        }

        validateImageSize(Files.size(path), path.toAbsolutePath().toString());

        return Files.readAllBytes(path);
    }

    private byte[] readAllBytes(InputStream inputStream, String source) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
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

        return outputStream.toByteArray();
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

    private ImageAsset.ImageFormat detectFormat(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return ImageAsset.ImageFormat.JPEG;
        }

        return ImageAsset.ImageFormat.OTHER;
    }

    private ImageSize readImageSize(byte[] bytes, String source) throws IOException {
        ImageSize jpegSize = tryReadJpegSize(bytes);
        if (jpegSize != null) {
            return jpegSize;
        }

        try (ImageInputStream imageInputStream = createImageInputStream(bytes, source)) {
            ImageReader imageReader = getImageReader(imageInputStream, source);

            try {
                imageInputStream.seek(0);
                imageReader.setInput(imageInputStream, true, true);

                return new ImageSize(
                        imageReader.getWidth(0),
                        imageReader.getHeight(0)
                );
            } finally {
                imageReader.dispose();
            }
        }
    }

    /**
     * Reads the JPEG SOF marker's Nf (number of components) field directly from bytes.
     * Returns 1=Gray, 3=RGB/YCbCr, 4=CMYK/YCCK, -1 if not JPEG or malformed.
     */
    public int tryReadJpegComponents(byte[] bytes) {
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

    private ImageSize tryReadJpegSize(byte[] bytes) {
        if (bytes.length < 4
                || (bytes[0] & 0xFF) != 0xFF
                || (bytes[1] & 0xFF) != 0xD8) {
            return null;
        }

        int i = 2;
        while (i + 4 <= bytes.length) {
            while (i < bytes.length && (bytes[i] & 0xFF) == 0xFF) {
                i++;
            }
            if (i >= bytes.length) {
                return null;
            }

            int marker = bytes[i] & 0xFF;
            i++;

            if (marker == 0x00 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD9)) {
                continue;
            }

            if (i + 2 > bytes.length) {
                return null;
            }

            int segmentLength = ((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF);
            if (segmentLength < 2) {
                return null;
            }

            boolean isStartOfFrame = marker >= 0xC0 && marker <= 0xCF
                    && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;

            if (isStartOfFrame) {
                if (i + 7 > bytes.length) {
                    return null;
                }
                int height = ((bytes[i + 3] & 0xFF) << 8) | (bytes[i + 4] & 0xFF);
                int width = ((bytes[i + 5] & 0xFF) << 8) | (bytes[i + 6] & 0xFF);
                if (width <= 0 || height <= 0) {
                    return null;
                }
                return new ImageSize(width, height);
            }

            i += segmentLength;
        }
        return null;
    }

    private BufferedImage readImage(byte[] bytes, String source, int subsampling) throws IOException {
        try (ImageInputStream imageInputStream = createImageInputStream(bytes, source)) {
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

    private ImageInputStream createImageInputStream(byte[] bytes, String source) throws IOException {
        ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));

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

    private record ImageSize(int width, int height) {
    }
}
