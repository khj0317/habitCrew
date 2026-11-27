package com.habitcrew.domain.guest;

import com.habitcrew.domain.certification.CertificationPhotos;
import com.habitcrew.domain.certification.ImageProcessor;
import com.habitcrew.global.storage.PhotoStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 체험 크루에 쓰는 샘플 인증 사진. 처음 한 번만 그려서 저장소에 올리고, 모든 체험 계정이 같은 사진을 같이 쓴다
 * (체험 계정마다 사진을 새로 올리면 저장 공간이 계속 늘어나므로).
 * 글자는 숫자·영문만 써서, 한글 글꼴이 없는 서버(리눅스 컨테이너)에서도 깨지지 않게 했다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SamplePhotos {

    private static final String MARKER = "guest_samples_v1";

    public record Photo(String imageKey, String thumbnailKey, int width, int height) {
    }

    private record Theme(Color from, Color to, String label, String caption) {
    }

    private static final List<Theme> THEMES = List.of(
            new Theme(new Color(0xfde68a), new Color(0xf59e0b), "p.128", "BOOK"),
            new Theme(new Color(0xbbf7d0), new Color(0x16a34a), "5.2 km", "RUN"),
            new Theme(new Color(0xbae6fd), new Color(0x0284c7), "2.0 L", "WATER"),
            new Theme(new Color(0xfecdd3), new Color(0xe11d48), "06:00", "WAKE UP"),
            new Theme(new Color(0xddd6fe), new Color(0x7c3aed), "30 min", "STUDY"),
            new Theme(new Color(0xfed7aa), new Color(0xea580c), "p.164", "BOOK"),
            new Theme(new Color(0xccfbf1), new Color(0x0d9488), "10,000", "STEPS"),
            new Theme(new Color(0xe7e5e4), new Color(0x57534e), "DAY 14", "JOURNAL"));

    private final JdbcTemplate jdbc;
    private final PhotoStorage storage;
    private final ImageProcessor imageProcessor;

    private volatile List<Photo> cached;

    public List<Photo> photos() {
        List<Photo> photos = cached;
        if (photos == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = loadOrCreate();
                }
                photos = cached;
            }
        }
        return photos;
    }

    private List<Photo> loadOrCreate() {
        List<String> stored = jdbc.queryForList("select value from app_secrets where name = ?", String.class, MARKER);
        if (stored.isEmpty()) {
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < THEMES.size(); i++) {
                ImageProcessor.Result image = imageProcessor.process(draw(THEMES.get(i)));
                String base = CertificationPhotos.SAMPLE_PREFIX + "v1/" + i;
                storage.put(base + ".jpg", image.display(), "image/jpeg");
                storage.put(base + "_thumb.jpg", image.thumbnail(), "image/jpeg");
                value.append(base).append(".jpg|").append(base).append("_thumb.jpg|")
                        .append(image.width()).append('|').append(image.height()).append('\n');
            }
            // 서버 두 대가 동시에 만들어도 먼저 저장한 쪽 목록을 함께 쓴다 (같은 경로라 사진도 같다)
            jdbc.update("insert into app_secrets (name, value) values (?, ?) on conflict do nothing", MARKER, value.toString());
            log.info("체험용 샘플 사진 {}장을 만들었습니다.", THEMES.size());
            stored = jdbc.queryForList("select value from app_secrets where name = ?", String.class, MARKER);
        }
        List<Photo> photos = new ArrayList<>();
        Arrays.stream(stored.getFirst().split("\n")).filter(line -> !line.isBlank()).forEach(line -> {
            String[] p = line.split("\\|");
            photos.add(new Photo(p[0], p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3])));
        });
        return List.copyOf(photos);
    }

    private static byte[] draw(Theme theme) {
        int w = 1200;
        int h = 900;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, theme.from(), w, h, theme.to()));
        g.fillRect(0, 0, w, h);

        // 가운데 흰 카드와 체크 표시
        g.setColor(new Color(255, 255, 255, 230));
        g.fill(new RoundRectangle2D.Double(250, 170, 700, 560, 80, 80));
        g.setColor(theme.to());
        g.setStroke(new BasicStroke(34, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[]{470, 560, 730}, new int[]{400, 490, 310}, 3);

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 96));
        drawCentered(g, theme.label(), w / 2, 650);
        g.setColor(new Color(255, 255, 255, 220));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        drawCentered(g, theme.caption(), w / 2, 830);
        g.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int baseline) {
        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, centerX - width / 2, baseline);
    }
}
