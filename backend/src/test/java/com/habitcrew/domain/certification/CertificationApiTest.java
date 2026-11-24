package com.habitcrew.domain.certification;

import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CertificationApiTest extends ApiTestSupport {

    // 2030-01-07은 월요일. KST = UTC+9
    private static final Instant MONDAY_NOON = Instant.parse("2030-01-07T03:00:00Z");
    private static final Instant MONDAY_23_59_59 = Instant.parse("2030-01-07T14:59:59Z");
    private static final Instant TUESDAY_00_00_05 = Instant.parse("2030-01-07T15:00:05Z");
    private static final List<String> EVERY_DAY =
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    CertificationService certificationService;

    // ---------- 도우미 ----------

    private record Crew(long id, String code, TestUser owner) {
    }

    private Crew newCrew(List<String> certDays) throws Exception {
        TestUser owner = newUser();
        JsonNode crew = json(perform(post("/api/crews"), owner, Map.of(
                "name", "테스트 크루", "certDays", certDays, "penaltyPoints", 1000, "maxMembers", 30)));
        return new Crew(crew.get("id").asLong(), crew.get("inviteCode").asString(), owner);
    }

    private TestUser join(Crew crew) throws Exception {
        TestUser user = newUser();
        perform(post("/api/crews/join"), user, Map.of("inviteCode", crew.code())).andExpect(status().isOk());
        return user;
    }

    private ResultActions certify(Crew crew, TestUser user, byte[] photo, String message) throws Exception {
        var request = multipart("/api/crews/" + crew.id() + "/certifications")
                .file(new MockMultipartFile("photo", "photo.png", "image/png", photo));
        if (message != null) {
            request.param("message", message);
        }
        return mockMvc.perform(request.header("Authorization", user.bearer()));
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(34, 197, 94));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private HttpResponse<byte[]> fetch(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    // ---------- 업로드·저장소 ----------

    @Test
    @DisplayName("사진을 올리면 큰 사진과 썸네일이 JPEG로 저장되고, 서명된 주소로만 볼 수 있다")
    void uploadAndSignedUrl() throws Exception {
        Crew crew = newCrew(EVERY_DAY);

        JsonNode item = json(certify(crew, crew.owner(), png(3000, 2000), "  오늘도 해냈다  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("오늘도 해냈다"))
                .andExpect(jsonPath("$.imageWidth").value(1600))
                .andExpect(jsonPath("$.imageHeight").value(1067))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.likeCount").value(0)));

        HttpResponse<byte[]> display = fetch(item.get("imageUrl").asString());
        HttpResponse<byte[]> thumbnail = fetch(item.get("thumbnailUrl").asString());
        assertThat(display.statusCode()).isEqualTo(200);
        assertThat(display.headers().firstValue("Content-Type")).hasValue("image/jpeg");
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(thumbnail.body()));
        assertThat(thumb.getWidth()).isEqualTo(480);
        assertThat(thumbnail.body().length).isLessThan(display.body().length);

        // 서명(쿼리 문자열)을 떼면 열 수 없다: 버킷이 공개되어 있지 않다
        String unsigned = item.get("imageUrl").asString().split("\\?")[0];
        assertThat(fetch(unsigned).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("이미지가 아닌 파일은 확장자·Content-Type을 속여도 거절한다")
    void rejectNonImage() throws Exception {
        Crew crew = newCrew(EVERY_DAY);

        certify(crew, crew.owner(), "<script>alert(1)</script>".getBytes(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
    }

    @Test
    @DisplayName("너무 작은 사진은 거절한다")
    void rejectTinyImage() throws Exception {
        Crew crew = newCrew(EVERY_DAY);

        certify(crew, crew.owner(), png(10, 10), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_SMALL"));
    }

    // ---------- 인증 규칙 ----------

    @Test
    @DisplayName("하루에 한 번만 인증할 수 있다")
    void oncePerDay() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        certify(crew, crew.owner(), png(800, 600), null).andExpect(status().isCreated());

        certify(crew, crew.owner(), png(800, 600), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CERTIFIED"));
    }

    @Test
    @DisplayName("인증일이 아닌 요일에는 인증할 수 없다")
    void notCertDay() throws Exception {
        Crew crew = newCrew(List.of("MONDAY"));
        clock.set(TUESDAY_00_00_05);

        certify(crew, crew.owner(), png(800, 600), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_CERT_DAY"));
    }

    @Test
    @DisplayName("자정 경계: 23:59:59에 도착한 요청은 처리가 00:00을 넘겨 끝나도 그날(월요일) 인증으로 기록된다")
    void midnightBoundary() throws Exception {
        Crew crew = newCrew(List.of("MONDAY"));
        clock.set(TUESDAY_00_00_05); // 서버가 저장하는 시점은 이미 화요일

        var item = certificationService.create(crew.owner().id(), crew.id(), png(800, 600), null, MONDAY_23_59_59);

        assertThat(item.certDate()).isEqualTo(LocalDate.of(2030, 1, 7));
    }

    @Test
    @DisplayName("동시성: 같은 사람이 인증을 동시에 5번 올려도 하나만 저장된다")
    void concurrentDuplicateCertification() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        byte[] photo = png(800, 600);
        clock.set(MONDAY_NOON);

        List<Object> results = runConcurrently(5, i -> {
            try {
                return certificationService.create(crew.owner().id(), crew.id(), photo, null, MONDAY_NOON);
            } catch (BusinessException e) {
                return e.getErrorCode();
            }
        });

        assertThat(results).filteredOn(r -> r instanceof ErrorCode).hasSize(4).containsOnly(ErrorCode.ALREADY_CERTIFIED);
        perform(get("/api/crews/" + crew.id() + "/certifications"), crew.owner())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    // ---------- 피드 ----------

    @Test
    @DisplayName("피드는 최신순 10개씩 나오고, 커서로 다음 페이지를 중복 없이 가져온다")
    void feedPagination() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        byte[] photo = png(400, 300);
        List<TestUser> users = new ArrayList<>(List.of(crew.owner()));
        for (int i = 0; i < 11; i++) {
            users.add(join(crew));
        }
        for (TestUser user : users) {
            certify(crew, user, photo, null).andExpect(status().isCreated());
        }

        JsonNode first = json(perform(get("/api/crews/" + crew.id() + "/certifications"), crew.owner()));
        assertThat(first.get("items").size()).isEqualTo(10);
        assertThat(first.get("items").get(0).get("author").get("userId").asLong()).isEqualTo(users.getLast().id());

        long cursor = first.get("nextCursor").asLong();
        JsonNode second = json(perform(get("/api/crews/" + crew.id() + "/certifications?cursor=" + cursor), crew.owner()));
        assertThat(second.get("items").size()).isEqualTo(2);
        assertThat(second.get("nextCursor").isNull()).isTrue();

        Set<Long> ids = new HashSet<>();
        first.get("items").forEach(n -> ids.add(n.get("id").asLong()));
        second.get("items").forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids).hasSize(12);
    }

    @Test
    @DisplayName("크루원이 아니면 피드도, 인증 하나도 볼 수 없다")
    void nonMember() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        long id = json(certify(crew, crew.owner(), png(400, 300), null)).get("id").asLong();
        TestUser stranger = newUser();

        perform(get("/api/crews/" + crew.id() + "/certifications"), stranger).andExpect(status().isNotFound());
        perform(get("/api/certifications/" + id), stranger)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CERTIFICATION_NOT_FOUND"));
        perform(put("/api/certifications/" + id + "/like"), stranger).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("오늘의 인증 현황: 누가 인증했고 마감이 언제인지 알려 준다")
    void todayStatus() throws Exception {
        clock.set(MONDAY_NOON);
        Crew crew = newCrew(List.of("MONDAY", "WEDNESDAY"));
        TestUser friend = join(crew);
        long id = json(certify(crew, friend, png(400, 300), null)).get("id").asLong();

        perform(get("/api/crews/" + crew.id() + "/certifications/today"), crew.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2030-01-07"))
                .andExpect(jsonPath("$.certDay").value(true))
                .andExpect(jsonPath("$.deadline").value("2030-01-07T15:00:00Z"))
                .andExpect(jsonPath("$.myCertificationId").doesNotExist())
                .andExpect(jsonPath("$.certifiedCount").value(1))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.members[1].certified").value(true))
                .andExpect(jsonPath("$.members[1].certificationId").value(id));
    }

    // ---------- 좋아요 ----------

    @Test
    @DisplayName("좋아요는 여러 번 눌러도 한 번만 반영되고, 취소도 마찬가지다")
    void likeIsIdempotent() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        TestUser friend = join(crew);
        long id = json(certify(crew, crew.owner(), png(400, 300), null)).get("id").asLong();

        perform(put("/api/certifications/" + id + "/like"), friend).andExpect(jsonPath("$.likeCount").value(1));
        perform(put("/api/certifications/" + id + "/like"), friend).andExpect(jsonPath("$.likeCount").value(1));
        perform(get("/api/certifications/" + id), friend).andExpect(jsonPath("$.likedByMe").value(true));

        perform(delete("/api/certifications/" + id + "/like"), friend).andExpect(jsonPath("$.likeCount").value(0));
        perform(delete("/api/certifications/" + id + "/like"), friend)
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    @DisplayName("동시성: 10명이 동시에 좋아요를 눌러도 개수가 정확히 10이다")
    void concurrentLikes() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        List<TestUser> friends = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            friends.add(join(crew));
        }
        long id = json(certify(crew, crew.owner(), png(400, 300), null)).get("id").asLong();

        runConcurrently(10, i -> certificationService.like(friends.get(i).id(), id));

        perform(get("/api/certifications/" + id), crew.owner()).andExpect(jsonPath("$.likeCount").value(10));
    }

    // ---------- 댓글 ----------

    @Test
    @DisplayName("댓글을 달면 개수가 늘고, 남의 댓글은 지울 수 없다")
    void comments() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        TestUser friend = join(crew);
        long id = json(certify(crew, crew.owner(), png(400, 300), null)).get("id").asLong();

        long commentId = json(perform(post("/api/certifications/" + id + "/comments"), friend,
                Map.of("content", "  멋져요!  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("멋져요!"))
                .andExpect(jsonPath("$.mine").value(true))).get("id").asLong();

        perform(get("/api/certifications/" + id), crew.owner()).andExpect(jsonPath("$.commentCount").value(1));
        perform(get("/api/certifications/" + id + "/comments"), crew.owner())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mine").value(false));

        perform(delete("/api/certifications/" + id + "/comments/" + commentId), crew.owner())
                .andExpect(status().isForbidden());
        perform(delete("/api/certifications/" + id + "/comments/" + commentId), friend)
                .andExpect(status().isNoContent());
        perform(get("/api/certifications/" + id), crew.owner()).andExpect(jsonPath("$.commentCount").value(0));

        perform(post("/api/certifications/" + id + "/comments"), friend, Map.of("content", "   "))
                .andExpect(status().isBadRequest());
    }

    // ---------- 삭제 ----------

    @Test
    @DisplayName("오늘 올린 내 인증은 지울 수 있고, 지우면 사진도 저장소에서 사라진다")
    void deleteToday() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        JsonNode item = json(certify(crew, crew.owner(), png(400, 300), null));
        long id = item.get("id").asLong();

        perform(delete("/api/certifications/" + id), crew.owner()).andExpect(status().isNoContent());

        perform(get("/api/certifications/" + id), crew.owner()).andExpect(status().isNotFound());
        assertThat(fetch(item.get("imageUrl").asString()).statusCode()).isEqualTo(404);
        // 지운 뒤에는 다시 인증할 수 있다
        certify(crew, crew.owner(), png(400, 300), null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("지난 날짜의 인증과 남의 인증은 지울 수 없다")
    void cannotDeletePastOrOthers() throws Exception {
        Crew crew = newCrew(EVERY_DAY);
        TestUser friend = join(crew);
        clock.set(MONDAY_NOON);
        long id = json(certify(crew, crew.owner(), png(400, 300), null)).get("id").asLong();

        perform(delete("/api/certifications/" + id), friend)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_CERTIFICATION_AUTHOR"));

        clock.set(TUESDAY_00_00_05);
        perform(delete("/api/certifications/" + id), crew.owner())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAST_CERTIFICATION"));
    }

    @Test
    @DisplayName("한마디가 200자를 넘으면 거절한다")
    void messageTooLong() {
        assertThatThrownBy(() -> certificationService.create(1L, 1L, new byte[0], "가".repeat(201), Instant.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200자");
    }

    // ---------- 동시 실행 도우미 ----------

    private interface IndexedTask {
        Object run(int index) throws Exception;
    }

    private static List<Object> runConcurrently(int count, IndexedTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.run(index);
            }));
        }
        ready.await();
        start.countDown();
        List<Object> results = new ArrayList<>();
        for (var future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }
}
