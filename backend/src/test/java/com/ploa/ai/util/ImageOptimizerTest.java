package com.ploa.ai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageOptimizerTest {

    private final ImageOptimizer optimizer = new ImageOptimizer();

    @Test
    @DisplayName("Should downscale images exceeding max dimension (1280px)")
    void shouldDownscaleOversizedImage() throws Exception {
        // Create 2000x1500 test image
        BufferedImage img = new BufferedImage(2000, 1500, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        byte[] input = baos.toByteArray();

        byte[] optimized = optimizer.optimizeForOcr(input, "image/jpeg");

        assertThat(optimized).isNotNull();
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(optimized));
        assertThat(result.getWidth()).isLessThanOrEqualTo(ImageOptimizer.DEFAULT_MAX_DIMENSION);
        assertThat(result.getHeight()).isLessThanOrEqualTo(ImageOptimizer.DEFAULT_MAX_DIMENSION);
        // Aspect ratio preserved: 2000:1500 = 4:3 -> 1280:960
        assertThat(result.getWidth()).isEqualTo(1280);
        assertThat(result.getHeight()).isEqualTo(960);
    }

    @Test
    @DisplayName("Should return original bytes gracefully if input is empty or null")
    void shouldHandleEmptyGracefully() {
        assertThat(optimizer.optimizeForOcr(null, "image/jpeg")).isNull();
        assertThat(optimizer.optimizeForOcr(new byte[0], "image/jpeg")).isEmpty();
    }
}
