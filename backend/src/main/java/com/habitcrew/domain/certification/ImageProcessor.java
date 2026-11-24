package com.habitcrew.domain.certification;

import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import org.springframework.stereotype.Component;

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
import java.util.Iterator;

/**
 * 올라온 사진을 검사하고 두 가지 크기의 JPEG로 다시 만든다.
 * - 파일 이름·Content-Type은 속일 수 있으므로 파일 앞부분(매직 바이트)으로 JPEG·PNG인지 확인한다
 * - 실제로 그리기 전에 가로·세로 크기부터 읽어, 작은 파일이 수억 픽셀로 풀리는 "압축 폭탄"을 막는다
 * - 새로 그려서 저장하므로 원본의 EXIF(촬영 위치 GPS 등)가 모두 사라진다
 * - 피드에는 작은 썸네일, 크게 볼 때만 큰 사진을 보내 데이터 사용량을 줄인다
 */
@Component
public class ImageProcessor {

    static final int DISPLAY_MAX = 1600;
    static final int THUMBNAIL_MAX = 480;
    private static final long MAX_PIXELS = 40_000_000L; // 약 4000만 화소 (최신 스마트폰 원본보다 넉넉함)
    private static final int MIN_SIDE = 64;

    public record Result(byte[] display, byte[] thumbnail, int width, int height) {
    }

    public Result process(byte[] input) {
        if (!isJpeg(input) && !isPng(input)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }
        BufferedImage source = read(input);
        BufferedImage display = resize(source, DISPLAY_MAX);
        BufferedImage thumbnail = resize(source, THUMBNAIL_MAX);
        return new Result(encodeJpeg(display, 0.85f), encodeJpeg(thumbnail, 0.8f), display.getWidth(), display.getHeight());
    }

    private static BufferedImage read(byte[] input) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width * height > MAX_PIXELS) {
                    throw new BusinessException(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
                }
                if (width < MIN_SIDE || height < MIN_SIDE) {
                    throw new BusinessException(ErrorCode.IMAGE_TOO_SMALL);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException business) {
                throw business;
            }
            // 헤더만 JPEG인 깨진 파일 등
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE);
        }
    }

    /** 긴 변이 max를 넘으면 비율을 유지해 줄인다. 투명한 PNG는 흰 배경 위에 그린다 (JPEG는 투명도가 없음) */
    static BufferedImage resize(BufferedImage source, int max) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) max / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage current = source;
        // 한 번에 크게 줄이면 계단 현상이 생겨서, 절반씩 여러 번 줄인 뒤 마지막에 맞춘다
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return draw(current, targetWidth, targetHeight);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG로 저장하지 못했습니다.", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }
}
