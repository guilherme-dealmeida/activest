package com.primevest.domain.user;

import com.primevest.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserAvatarServiceTest {

    public static byte[] imageBytes(String format, int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    static BufferedImage readBack(byte[] b) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(b));
    }

    @Test
    @DisplayName("PNG retangular vira JPEG quadrado (recorte central), reduzido a 512")
    void cropsAndShrinks() throws IOException {
        byte[] jpeg = UserAvatarService.normalize(imageBytes("png", 1200, 800));
        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF);
        BufferedImage r = readBack(jpeg);
        assertThat(r.getWidth()).isEqualTo(512);
        assertThat(r.getHeight()).isEqualTo(512);
    }

    @Test
    @DisplayName("Imagem entre 128 e 512 px nao e ampliada")
    void doesNotWiden() throws IOException {
        BufferedImage r = readBack(UserAvatarService.normalize(imageBytes("jpg", 300, 300)));
        assertThat(r.getWidth()).isEqualTo(300);
    }

    @Test
    @DisplayName("Recusa: pequena demais, formato nao aceito (pelos bytes) e arquivo corrompido")
    void rejections() throws IOException {
        assertThatThrownBy(() -> UserAvatarService.normalize(imageBytes("png", 100, 400)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("128");
        assertThatThrownBy(() -> UserAvatarService.normalize(imageBytes("gif", 300, 300)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("Formato");
        assertThatThrownBy(() -> UserAvatarService.normalize(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3}))
            .isInstanceOf(BusinessException.class).hasMessageContaining("inválido");
    }
}
