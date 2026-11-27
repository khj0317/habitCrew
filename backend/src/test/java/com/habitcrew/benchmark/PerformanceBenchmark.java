package com.habitcrew.benchmark;

import com.habitcrew.domain.certification.ImageProcessor;
import com.habitcrew.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 성능 측정. 일반 테스트에서는 돌지 않고 `./gradlew benchmark`로만 돈다.
 * 실제 PostgreSQL·Redis 컨테이너에서 재며, 결과는 마크다운 표로 build/benchmark/result.md에 쓴다.
 * 컨테이너·노트북 성능에 따라 절대값은 달라지므로 "비율"을 본다.
 */
@Tag("benchmark")
class PerformanceBenchmark extends ApiTestSupport {

    private static final int MEMBERS = 30;
    private static final int DAYS = 6_700; // 30명 × 6,700일 ≈ 20만 건
    private static final int PAGE = 10;
    private static final int RUNS = 7;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    ImageProcessor imageProcessor;

    private final StringBuilder report = new StringBuilder();

    @Test
    @DisplayName("성능 측정")
    void run() throws Exception {
        report.append("# HabitCrew 성능 측정 결과\n\n");
        report.append("- 환경: Testcontainers PostgreSQL 17 · Redis 7, 측정마다 ").append(RUNS)
                .append("번 돌린 중앙값\n\n");
        long crewId = seedLargeCrew();
        feedPagination(crewId);
        ranking();
        images();
        Path output = Path.of(System.getProperty("benchmark.output", "build/benchmark/result.md"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, report.toString());
        System.out.println(report);
    }

    /** 한 크루에 크루원 30명 × 6,700일 = 약 20만 건의 인증을 SQL로 한 번에 넣는다 */
    private long seedLargeCrew() throws Exception {
        TestUser owner = newUser();
        JsonNode crew = json(perform(post("/api/crews"), owner, Map.of("name", "측정 크루",
                "certDays", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                "penaltyPoints", 0, "maxMembers", 30)));
        long crewId = crew.get("id").asLong();
        List<Long> userIds = new ArrayList<>(List.of(owner.id()));
        for (int i = 1; i < MEMBERS; i++) {
            userIds.add(newUser().id());
        }
        for (Long userId : userIds) {
            jdbc.update("""
                    insert into certifications (crew_id, user_id, cert_date, image_key, thumbnail_key,
                                                image_width, image_height, created_at)
                    select ?, ?, date '2000-01-01' + d, 'bench.jpg', 'bench_thumb.jpg', 1600, 1200,
                           timestamp '2000-01-01' + d * interval '1 day'
                    from generate_series(0, ? - 1) d
                    """, crewId, userId, DAYS);
        }
        jdbc.execute("analyze certifications");
        Integer total = jdbc.queryForObject("select count(*) from certifications where crew_id = ?", Integer.class, crewId);
        report.append("## 1. 피드 페이지네이션: OFFSET vs 커서\n\n");
        report.append("한 크루에 인증 ").append(String.format("%,d", total)).append("건, 한 페이지 ").append(PAGE)
                .append("개. `EXPLAIN ANALYZE`의 실행 시간(ms)\n\n");
        return crewId;
    }

    private void feedPagination(long crewId) {
        List<Long> ids = jdbc.queryForList("select id from certifications where crew_id = ? order by id desc", Long.class, crewId);
        report.append("| 몇 번째 글부터 | OFFSET (ms) | 커서 (ms) | 차이 |\n|---:|---:|---:|---:|\n");
        for (int offset : new int[]{0, 1_000, 10_000, 100_000, 190_000}) {
            long cursor = ids.get(Math.max(0, offset - 1)) + (offset == 0 ? 1 : 0);
            double offsetMs = median(() -> explainMs("""
                    select c.id from certifications c join users u on u.id = c.user_id
                    where c.crew_id = %d order by c.id desc limit %d offset %d
                    """.formatted(crewId, PAGE + 1, offset)));
            double cursorMs = median(() -> explainMs("""
                    select c.id from certifications c join users u on u.id = c.user_id
                    where c.crew_id = %d and c.id < %d order by c.id desc limit %d
                    """.formatted(crewId, cursor, PAGE + 1)));
            report.append(String.format("| %,d | %.3f | %.3f | %.0f배 |%n", offset, offsetMs, cursorMs,
                    Math.max(1, offsetMs / Math.max(cursorMs, 0.001))));
        }
        report.append("\nOFFSET은 앞의 글을 모두 읽고 버리므로 뒤로 갈수록 느려지고, 커서는 인덱스 `(crew_id, id DESC)`에서 ")
                .append("바로 그 위치로 가서 어느 페이지든 비슷하다.\n\n");
    }

    private double explainMs(String sql) {
        List<String> plan = jdbc.queryForList("explain (analyze, format text) " + sql, String.class);
        String line = plan.stream().filter(l -> l.startsWith("Execution Time")).findFirst().orElseThrow();
        return Double.parseDouble(line.replaceAll("[^0-9.]", ""));
    }

    /** 실제 크기의 크루(30명 × 한 달)에서 랭킹을 DB 집계와 Redis로 각각 1,000번 읽는다 */
    private void ranking() throws Exception {
        TestUser owner = newUser();
        long crewId = json(perform(post("/api/crews"), owner, Map.of("name", "랭킹 측정",
                "certDays", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"),
                "penaltyPoints", 0, "maxMembers", 30))).get("id").asLong();
        Random random = new Random(42);
        String key = "bench:rank:" + crewId;
        redis.delete(key);
        for (int m = 0; m < MEMBERS; m++) {
            long userId = m == 0 ? owner.id() : newUser().id();
            int days = 10 + random.nextInt(21);
            jdbc.update("""
                    insert into certifications (crew_id, user_id, cert_date, image_key, thumbnail_key,
                                                image_width, image_height, created_at)
                    select ?, ?, date '2031-01-01' + d, 'b.jpg', 'b_t.jpg', 1, 1, now() from generate_series(0, ? - 1) d
                    """, crewId, userId, days);
            redis.opsForZSet().add(key, String.valueOf(userId), days);
        }
        int iterations = 1_000;
        double dbMs = median(() -> timeMs(iterations, () -> jdbc.queryForList("""
                select user_id, count(*) c from certifications
                where crew_id = ? and cert_date between date '2031-01-01' and date '2031-01-31'
                group by user_id order by c desc
                """, crewId)));
        double redisMs = median(() -> timeMs(iterations, () -> redis.opsForZSet().reverseRangeWithScores(key, 0, -1)));
        report.append("## 2. 월간 랭킹: DB 집계 vs Redis Sorted Set\n\n");
        report.append("실제 크기의 크루(30명 × 한 달, 약 600건). 한 번 조회하는 데 걸린 평균 시간(ms, 1,000번 평균)\n\n");
        report.append("| 방식 | 평균 (ms) |\n|---|---:|\n");
        report.append(String.format("| DB `GROUP BY` + `ORDER BY` | %.3f |%n", dbMs));
        report.append(String.format("| Redis `ZREVRANGE` | %.3f |%n%n", redisMs));
        report.append("이 규모에서는 둘 다 1ms 안팎이라 사용자가 느끼는 차이는 없다. Redis 랭킹은 성능보다는 ")
                .append("대상이 커질 때(전체 사용자 랭킹 등)를 대비한 구조이고, 그때 생기는 캐시 일관성 문제를 미리 다뤄 본 데 의미가 있다.\n\n");
        redis.delete(key);
    }

    /** 스마트폰 사진과 비슷한 4000×3000 JPEG(품질 95)를 서버가 줄였을 때의 크기 */
    private void images() throws Exception {
        byte[] original = phoneLikePhoto(4000, 3000);
        long start = System.nanoTime();
        ImageProcessor.Result result = imageProcessor.process(original);
        double processMs = (System.nanoTime() - start) / 1_000_000.0;
        report.append("## 3. 사진 크기\n\n");
        report.append("스마트폰 사진과 비슷하게 잡음을 넣은 4000×3000 JPEG(품질 95)를 서버가 처리했을 때\n\n");
        report.append("| 파일 | 크기 | 원본 대비 |\n|---|---:|---:|\n");
        report.append(String.format("| 원본 (4000×3000) | %s | 100%% |%n", kb(original.length)));
        report.append(String.format("| 크게 보기 (%d×%d) | %s | %.0f%% |%n", result.width(), result.height(),
                kb(result.display().length), 100.0 * result.display().length / original.length));
        report.append(String.format("| 피드 썸네일 (480px) | %s | %.1f%% |%n%n", kb(result.thumbnail().length),
                100.0 * result.thumbnail().length / original.length));
        report.append(String.format("처리 시간(디코딩·축소·인코딩 두 번): %.0f ms. ", processMs));
        report.append("피드 10개를 볼 때 원본이라면 약 ").append(kb(original.length * 10L)).append(", 썸네일은 약 ")
                .append(kb(result.thumbnail().length * 10L)).append("를 받는다.\n");
    }

    private static byte[] phoneLikePhoto(int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x60a5fa), w, h, new Color(0xf59e0b)));
        g.fillRect(0, 0, w, h);
        g.dispose();
        Random random = new Random(7);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int noise = random.nextInt(25) - 12; // 센서 잡음처럼 픽셀마다 조금씩 다르게
                int r = clamp(((rgb >> 16) & 0xff) + noise);
                int gg = clamp(((rgb >> 8) & 0xff) + noise);
                int b = clamp((rgb & 0xff) + noise);
                image.setRGB(x, y, (r << 16) | (gg << 8) | b);
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f);
            writer.write(null, new IIOImage(image, null, null), param);
        }
        writer.dispose();
        return out.toByteArray();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static String kb(long bytes) {
        return bytes >= 1024 * 1024 ? String.format("%.1fMB", bytes / 1024.0 / 1024.0) : String.format("%,dKB", bytes / 1024);
    }

    private static double timeMs(int iterations, Runnable work) {
        for (int i = 0; i < 50; i++) {
            work.run(); // 워밍업
        }
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            work.run();
        }
        return (System.nanoTime() - start) / 1_000_000.0 / iterations;
    }

    private static double median(Supplier<Double> measure) {
        measure.get(); // 워밍업 (캐시 채우기)
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            values.add(measure.get());
        }
        Collections.sort(values);
        return values.get(values.size() / 2);
    }
}
