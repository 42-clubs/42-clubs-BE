package com.clubplatform.activity.storage;

import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PhotoValidator {
    public List<ValidatedPhoto> validate(List<MultipartFile> files) {
        if (files == null) return List.of();
        if (files.size() > 5) throw new IllegalArgumentException("사진은 최대 5장입니다.");
        var result = new ArrayList<ValidatedPhoto>();
        for (var file : files) {
            if (file.isEmpty()) throw new IllegalArgumentException("빈 사진은 허용하지 않습니다.");
            if (file.getSize() > 5242880) throw new MaxUploadSizeExceededException(5242880);
            try (var stream = file.getInputStream()) {
                byte[] bytes = stream.readNBytes(5242881);
                if (bytes.length > 5242880) throw new MaxUploadSizeExceededException(5242880);
                try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw new IllegalArgumentException("이미지를 읽을 수 없습니다.");
                    var reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                        if (!Set.of("jpeg", "jpg", "png", "webp").contains(format))
                            throw new IllegalArgumentException("JPEG·PNG·WebP만 허용합니다.");
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width <= 0 || height <= 0 || (long) width * height > 25000000)
                            throw new IllegalArgumentException("이미지 크기가 제한을 초과합니다.");
                        if (reader.read(0) == null)
                            throw new IllegalArgumentException("손상된 이미지입니다.");
                        String ext = "jpg".equals(format) ? "jpeg" : format;
                        result.add(new ValidatedPhoto(bytes, ext, "image/" + ext));
                    } finally {
                        reader.dispose();
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("손상된 이미지입니다.", e);
            }
        }
        return result;
    }
}
