package com.activest.domain.user;

import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.UUID;

/**
 * Foto de perfil enviada pelo usuário (docs/planos/PLANO_CADASTRO_USUARIO.md, adendo de 05/10/2026).
 *
 * <p>Os padrões de foto de perfil: JPEG ou PNG de até {@value #MAX_BYTES_MB} MB, com pelo menos
 * {@value #MIN_LADO} px no lado menor e no máximo {@value #MAX_LADO} px no maior. A tela já recorta o
 * quadrado e manda 512×512, mas a API não confia nisso: confere o tipo pelos bytes (não pela extensão),
 * lê as dimensões antes de decodificar (imagem gigante não chega à memória), recorta o quadrado central
 * se precisar e regrava como JPEG {@value #SAIDA}×{@value #SAIDA} — o que também apaga os metadados
 * (EXIF, localização) que vinham no arquivo.</p>
 */
@Service
public class UserAvatarService {

    static final int MAX_BYTES_MB = 2;
    static final long MAX_BYTES = MAX_BYTES_MB * 1024L * 1024L;
    static final int MIN_SIDE = 128;
    static final int MAX_SIDE = 4096;
    static final int OUTPUT_FORMAT = 512;
    private static final float QUALITY = 0.88f;

    private final UserAvatarRepository avatars;
    private final UserRepository users;

    public UserAvatarService(UserAvatarRepository avatars, UserRepository users) {
        this.avatars = avatars;
        this.users = users;
    }

    public record Avatar(byte[] image, String contentType, LocalDateTime updatedAt) {}

    @Transactional(readOnly = true)
    public Avatar get(UUID userId) {
        return avatars.findById(userId)
            .map(a -> new Avatar(a.getImage(), a.getContentType(), a.getUpdatedAt()))
            .orElseThrow(() -> new ResourceNotFoundException("Sem foto de perfil"));
    }

    @Transactional
    public void save(UUID userId, MultipartFile file) {
        if (!users.existsById(userId)) {
            throw new ResourceNotFoundException("Usuário não encontrado");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Escolha uma imagem.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("A imagem passa de " + MAX_BYTES_MB + " MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler a imagem.");
        }
        byte[] jpeg = normalize(bytes);
        UserAvatar avatar = avatars.findById(userId).orElseGet(() -> UserAvatar.builder().userId(userId).build());
        avatar.setImage(jpeg);
        avatar.setContentType("image/jpeg");
        avatar.setUpdatedAt(LocalDateTime.now());
        avatars.save(avatar);
    }

    @Transactional
    public void delete(UUID userId) {
        avatars.deleteById(userId);
    }

    /** Valida e regrava como JPEG quadrado. Público para os testes. */
    static byte[] normalize(byte[] bytes) {
        if (!jpeg(bytes) && !png(bytes)) {
            throw new BusinessException("Formato não aceito: envie uma imagem JPEG, PNG ou WebP.");
        }
        BufferedImage original = readImage(bytes);

        int w = original.getWidth();
        int h = original.getHeight();
        int side = Math.min(w, h);
        if (side < MIN_SIDE) {
            throw new BusinessException("A imagem é pequena demais: precisa de pelo menos " + MIN_SIDE + "×"
                + MIN_SIDE + " px.");
        }
        int output = Math.min(side, OUTPUT_FORMAT);
        BufferedImage square = new BufferedImage(output, output, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = square.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // PNG com transparência: o JPEG não tem canal alfa, o fundo vira branco
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, output, output);
            int x = (w - side) / 2;
            int y = (h - side) / 2;
            g.drawImage(original, 0, 0, output, output, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return writeJpeg(square);
    }

    /** Confere as dimensões no cabeçalho antes de decodificar: 20000×20000 não chega a ser alocado. */
    private static BufferedImage readImage(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (in == null || !readers.hasNext()) {
                throw new BusinessException("Arquivo de imagem inválido.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w > MAX_SIDE || h > MAX_SIDE) {
                    throw new BusinessException("A imagem é grande demais: no máximo " + MAX_SIDE + "×" + MAX_SIDE + " px.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException("Arquivo de imagem inválido.");
        }
    }

    private static byte[] writeJpeg(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(img, null, null), p);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar a imagem.", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static boolean jpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean png(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }
}
