package com.ploa.ai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * Optimizes receipt and transfer images before sending to Vision LLM.
 * Reduces vision token count (tiling costs) and network payload by resizing
 * excessively high-resolution photos (e.g. 4032x3024) to a clean 1280px max dimension
 * while maintaining 100% OCR legibility.
 */
@Component
public class ImageOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);
    public static final int DEFAULT_MAX_DIMENSION = 1280;
    public static final float JPEG_QUALITY = 0.85f;

    public byte[] optimizeForOcr(byte[] imageBytes, String mimeType) {
        if (imageBytes == null || imageBytes.length == 0) {
            return imageBytes;
        }

        // Skip non-standard formats like PDF
        if (mimeType != null && mimeType.contains("pdf")) {
            return imageBytes;
        }

        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                log.debug("ImageIO could not decode image, using original bytes");
                return imageBytes;
            }

            int width = original.getWidth();
            int height = original.getHeight();

            // If already compact and within bounds, no downscaling needed
            if (width <= DEFAULT_MAX_DIMENSION && height <= DEFAULT_MAX_DIMENSION && imageBytes.length < 350_000) {
                log.debug("Image already within optimal bounds ({}x{}, {} bytes)", width, height, imageBytes.length);
                return imageBytes;
            }

            // Calculate scaled dimensions preserving aspect ratio
            double scale = Math.min(
                    (double) DEFAULT_MAX_DIMENSION / width,
                    (double) DEFAULT_MAX_DIMENSION / height
            );
            if (scale > 1.0) {
                scale = 1.0;
            }

            int targetWidth = (int) Math.round(width * scale);
            int targetHeight = (int) Math.round(height * scale);

            // Draw to RGB image (removes alpha if present for clean JPEG compression)
            BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resized.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.drawImage(original, 0, 0, targetWidth, targetHeight, Color.WHITE, null);
            } finally {
                g2d.dispose();
            }

            // Encode to JPEG with high quality
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(JPEG_QUALITY);
                    }
                    writer.write(null, new IIOImage(resized, null, null), param);
                } finally {
                    writer.dispose();
                }
            } else {
                ImageIO.write(resized, "jpeg", baos);
            }

            byte[] optimizedBytes = baos.toByteArray();
            log.info("Optimized OCR image: {}x{} ({} bytes) -> {}x{} ({} bytes) [{}% reduction]",
                    width, height, imageBytes.length,
                    targetWidth, targetHeight, optimizedBytes.length,
                    (100 - (optimizedBytes.length * 100 / imageBytes.length)));

            return optimizedBytes;
        } catch (Throwable t) {
            log.warn("Image optimization skipped due to error: {}, using original bytes", t.getMessage());
            return imageBytes;
        }
    }
}
