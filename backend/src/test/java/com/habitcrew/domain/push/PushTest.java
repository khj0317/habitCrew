package com.habitcrew.domain.push;

import com.habitcrew.domain.certification.CertificationService;
import com.habitcrew.support.ApiTestSupport;
import com.habitcrew.support.RecordingPushGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushTest extends ApiTestSupport {

    private static final Instant MON_NOON = Instant.parse("2030-01-07T03:00:00Z"); // 월요일 정오 KST

    @Autowired
    RecordingPushGateway gateway;

    @Autowired
    DeadlineReminder reminder;

    @Autowired
    CertificationService certificationService;

    @AfterEach
    void resetGateway() {
        gateway.reset();
    }

    /** 브라우저 역할: 키 쌍과 auth 비밀값을 만들어 구독한다 */
    private record Browser(String endpoint, KeyPair keys, byte[] auth) {

        static Browser create() throws Exception {
            byte[] auth = new byte[16];
            new SecureRandom().nextBytes(auth);
            return new Browser("https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID(), EcKeys.generate(), auth);
        }

        Map<String, Object> subscription() {
            return Map.of("endpoint", endpoint, "keys", Map.of(
                    "p256dh", EcKeys.base64Url(EcKeys.rawPublic((ECPublicKey) keys.getPublic())),
                    "auth", EcKeys.base64Url(auth)));
        }

        /** RFC 8291 받는 쪽 순서대로 푼다 (서버 구현을 쓰지 않고 JDK만으로) */
        String decrypt(byte[] body) throws Exception {
            ByteBuffer buffer = ByteBuffer.wrap(body);
            byte[] salt = new byte[16];
            buffer.get(salt);
            int recordSize = buffer.getInt();
            assertThat(recordSize).isEqualTo(4096);
            byte[] asPublic = new byte[buffer.get()];
            buffer.get(asPublic);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
            ecdh.init(keys.getPrivate());
            ecdh.doPhase(EcKeys.publicFromRaw(asPublic), true);
            byte[] uaPublic = EcKeys.rawPublic((ECPublicKey) keys.getPublic());
            byte[] keyInfo = join("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = expand(hmac(auth, ecdh.generateSecret()), keyInfo, 32);
            byte[] prk = hmac(salt, ikm);
            byte[] cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] plain = cipher.doFinal(ciphertext);
            int end = plain.length - 1;
            while (end >= 0 && plain[end] == 0) {
                end--; // 패딩
            }
            assertThat(plain[end]).isEqualTo((byte) 0x02); // 마지막 레코드 표시
            return new String(plain, 0, end, StandardCharsets.UTF_8);
        }

        private static byte[] hmac(byte[] key, byte[] data) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        }

        private static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
            return Arrays.copyOf(hmac(prk, join(info, new byte[]{1})), length);
        }

        private static byte[] join(byte[]... parts) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] p : parts) {
                out.writeBytes(p);
            }
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("구독 후 시험 알림을 보내면, 브라우저 키로만 풀 수 있게 암호화되고 VAPID 서명이 붙는다")
    void encryptedAndSigned() throws Exception {
        TestUser user = newUser();
        Browser browser = Browser.create();
        perform(post("/api/push/subscriptions"), user, browser.subscription()).andExpect(status().isNoContent());
        perform(get("/api/push/status"), user).andExpect(jsonPath("$.subscriptionCount").value(1));
        String vapidPublic = json(perform(get("/api/push/public-key"), user)).get("publicKey").asString();

        perform(post("/api/push/test"), user).andExpect(jsonPath("$.delivered").value(1));

        RecordingPushGateway.Sent sent = gateway.sentTo(browser.endpoint()).getFirst();
        assertThat(sent.headers()).containsEntry("Content-Encoding", "aes128gcm").containsEntry("TTL", "3600");

        // 1) 브라우저만 내용을 풀 수 있다
        JsonNode payload = objectMapper.readTree(browser.decrypt(sent.body()));
        assertThat(payload.get("title").asString()).contains("알림이 켜졌어요");
        assertThat(payload.get("url").asString()).isEqualTo("/");

        // 2) VAPID: 푸시 서버 origin을 aud로 하고, 우리 공개 키로 서명이 검증된다
        String authorization = sent.headers().get("Authorization");
        assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + vapidPublic);
        String jwt = authorization.substring("vapid t=".length(), authorization.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.get("aud").asString()).isEqualTo("https://fcm.googleapis.com");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(EcKeys.publicFromRaw(EcKeys.fromBase64Url(vapidPublic)));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    @DisplayName("SSRF 방지: 알려진 푸시 서버가 아닌 주소나 잘못된 키로는 구독할 수 없다")
    void rejectsUntrustedEndpoints() throws Exception {
        TestUser user = newUser();
        Browser browser = Browser.create();
        Map<String, Object> keys = (Map<String, Object>) browser.subscription().get("keys");

        for (String endpoint : List.of(
                "http://fcm.googleapis.com/fcm/send/abc",          // https 아님
                "https://169.254.169.254/latest/meta-data",        // 클라우드 메타데이터 서버
                "https://evil.example.com/fcm.googleapis.com",     // 다른 도메인
                "https://fcm.googleapis.com.evil.com/send",        // 비슷한 도메인
                "https://user@fcm.googleapis.com/send")) {          // 사용자 정보 섞기
            perform(post("/api/push/subscriptions"), user, Map.of("endpoint", endpoint, "keys", keys))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PUSH_SUBSCRIPTION"));
        }
        perform(post("/api/push/subscriptions"), user, Map.of("endpoint", browser.endpoint(),
                "keys", Map.of("p256dh", "not-a-key", "auth", "AAAA")))
                .andExpect(status().isBadRequest());
        perform(post("/api/push/test"), user)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PUSH_NOT_SUBSCRIBED"));
    }

    @Test
    @DisplayName("푸시 서버가 410(구독 사라짐)을 주면 구독을 정리한다")
    void removesGoneSubscriptions() throws Exception {
        TestUser user = newUser();
        perform(post("/api/push/subscriptions"), user, Browser.create().subscription());
        gateway.respondWith(410);

        perform(post("/api/push/test"), user).andExpect(jsonPath("$.delivered").value(0));

        perform(get("/api/push/status"), user).andExpect(jsonPath("$.subscriptionCount").value(0));
    }

    @Test
    @DisplayName("마감 알림: 오늘 인증하지 않은 사람에게만, 하루 한 번만 보낸다")
    void deadlineReminder() throws Exception {
        clock.set(MON_NOON);
        TestUser lazy = newUser();
        TestUser diligent = newUser();
        JsonNode crew = json(perform(post("/api/crews"), lazy, Map.of(
                "name", "알림 크루", "certDays", List.of("MONDAY"), "penaltyPoints", 0, "maxMembers", 5)));
        perform(post("/api/crews/join"), diligent, Map.of("inviteCode", crew.get("inviteCode").asString()));
        Browser lazyPhone = Browser.create();
        Browser diligentPhone = Browser.create();
        perform(post("/api/push/subscriptions"), lazy, lazyPhone.subscription());
        perform(post("/api/push/subscriptions"), diligent, diligentPhone.subscription());
        certificationService.create(diligent.id(), crew.get("id").asLong(), png(), null, MON_NOON);

        reminder.sendToday();
        reminder.sendToday(); // 다시 돌아도 두 번 가지 않는다

        assertThat(gateway.sentTo(lazyPhone.endpoint())).hasSize(1);
        assertThat(gateway.sentTo(diligentPhone.endpoint())).isEmpty();
        JsonNode payload = objectMapper.readTree(lazyPhone.decrypt(gateway.sentTo(lazyPhone.endpoint()).getFirst().body()));
        assertThat(payload.get("body").asString()).contains("알림 크루");
        assertThat(payload.get("url").asString()).isEqualTo("/crews/" + crew.get("id").asLong());
    }

    @Test
    @DisplayName("같은 기기에서 다른 계정으로 구독하면 구독이 그 계정으로 옮겨진다")
    void resubscribeMovesOwnership() throws Exception {
        TestUser first = newUser();
        TestUser second = newUser();
        Browser browser = Browser.create();

        perform(post("/api/push/subscriptions"), first, browser.subscription());
        perform(post("/api/push/subscriptions"), second, browser.subscription());

        perform(get("/api/push/status"), first).andExpect(jsonPath("$.subscriptionCount").value(0));
        perform(get("/api/push/status"), second).andExpect(jsonPath("$.subscriptionCount").value(1));
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
