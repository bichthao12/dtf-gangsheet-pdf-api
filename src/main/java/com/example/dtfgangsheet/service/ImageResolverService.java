package com.example.dtfgangsheet.service;

import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

@Service
public class ImageResolverService {

    public BufferedImage rotate(BufferedImage source, double rotationDegree) {
        double normalizedRotation = normalizeRotation(rotationDegree);

        if (normalizedRotation == 0) {
            return deepCopy(source);
        }

        double radians = Math.toRadians(normalizedRotation);

        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        int rotatedWidth = (int) Math.floor(sourceWidth * cos + sourceHeight * sin);
        int rotatedHeight = (int) Math.floor(sourceHeight * cos + sourceWidth * sin);

        BufferedImage rotatedImage = new BufferedImage(
                rotatedWidth,
                rotatedHeight,
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = rotatedImage.createGraphics();

        try {
            g2d.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            g2d.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );
            g2d.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            AffineTransform transform = new AffineTransform();

            transform.translate(rotatedWidth / 2.0, rotatedHeight / 2.0);
            transform.rotate(radians);
            transform.translate(-sourceWidth / 2.0, -sourceHeight / 2.0);

            g2d.drawImage(source, transform, null);
        } finally {
            g2d.dispose();
        }

        return rotatedImage;
    }

    private double normalizeRotation(double rotationDegree) {
        double normalized = rotationDegree % 360;

        if (normalized < 0) {
            normalized += 360;
        }

        return normalized;
    }

    private BufferedImage deepCopy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2d = copy.createGraphics();

        try {
            g2d.drawImage(source, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        return copy;
    }
}