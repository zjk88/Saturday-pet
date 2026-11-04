package com.saturday.pet.app;

import java.io.IOException;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 统一校验上传图片并调用独立视觉模型。 */
@Component
public class PetImageService {
    private final PetVisionApp app;

    public PetImageService(PetVisionApp app) {
        this.app = app;
    }

    public String explain(String message, MultipartFile image) throws IOException {
        if (message.isBlank() || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题和图片不能为空");
        }
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 5MB");
        }
        String type = image.getContentType();
        if (type == null || !Set.of("image/jpeg", "image/png").contains(type)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 JPEG、PNG");
        }
        try (var input = image.getInputStream(); var stream = ImageIO.createImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片内容无效");
            }
            var reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg"))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片格式不匹配");
                }
                type = format.equalsIgnoreCase("png") ? "image/png" : "image/jpeg";
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片像素过大，请缩小后上传");
                }
            } finally {
                reader.dispose();
            }
        }
        return app.explain(message, image.getBytes(), type);
    }
}
