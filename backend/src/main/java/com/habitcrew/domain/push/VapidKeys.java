package com.habitcrew.domain.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * VAPID (RFC 8292): 푸시 서버에 "이 알림은 이 서버가 보낸 것"이라고 서명해서 증명한다.
 * 브라우저는 구독할 때 이 공개 키를 받아 두고, 같은 키로 서명된 알림만 받는다. 그래서 키가 바뀌면 기존 구독은 모두 무효가 된다.
 *
 * <p>키를 정하는 순서: 환경 변수(VAPID_PUBLIC_KEY·VAPID_PRIVATE_KEY) → DB에 저장된 키 → 없으면 새로 만들어 DB에 저장.
 * 서버 여러 대가 동시에 처음 뜨더라도 INSERT ... ON CONFLICT DO NOTHING 뒤 다시 읽으므로 모두 같은 키를 쓴다.
 */
@Slf4j
@Component
public class VapidKeys {

    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final ECPublicKey publicKey;
    private final ECPrivateKey privateKey;
    private final String publicKeyBase64;
    private final String subject;
    private final Clock clock;

    public VapidKeys(JdbcTemplate jdbc, Clock clock,
                     @Value("${habitcrew.push.subject}") String subject,
                     @Value("${habitcrew.push.public-key:}") String configuredPublic,
                     @Value("${habitcrew.push.private-key:}") String configuredPrivate) throws GeneralSecurityException {
        this.subject = subject;
        this.clock = clock;
        String[] keys = configuredPublic.isBlank() || configuredPrivate.isBlank()
                ? loadOrCreate(jdbc)
                : new String[]{configuredPublic, configuredPrivate};
        this.publicKey = EcKeys.publicFromRaw(EcKeys.fromBase64Url(keys[0]));
        this.privateKey = EcKeys.privateFromRaw(EcKeys.fromBase64Url(keys[1]));
        this.publicKeyBase64 = EcKeys.base64Url(EcKeys.rawPublic(publicKey));
    }

    /**
     * 공개 키와 비밀 키를 "공개.비밀" 한 줄로 저장한다. 두 줄로 나눠 저장하면 서버 두 대가 동시에 처음 뜰 때
     * A의 공개 키와 B의 비밀 키가 짝지어 저장될 수 있다 (그러면 모든 알림 서명이 틀린다).
     */
    private static String[] loadOrCreate(JdbcTemplate jdbc) throws GeneralSecurityException {
        KeyPair generated = EcKeys.generate();
        String pair = EcKeys.base64Url(EcKeys.rawPublic((ECPublicKey) generated.getPublic())) + "."
                + EcKeys.base64Url(EcKeys.rawPrivate((ECPrivateKey) generated.getPrivate()));
        int created = jdbc.update("insert into app_secrets (name, value) values ('vapid', ?) on conflict do nothing", pair);
        if (created == 1) {
            log.info("VAPID 키를 새로 만들어 저장했습니다.");
        }
        List<String> stored = jdbc.queryForList("select value from app_secrets where name = 'vapid'", String.class);
        return stored.getFirst().split("\\.");
    }

    /** 브라우저가 구독할 때 쓰는 공개 키 (applicationServerKey) */
    public String publicKey() {
        return publicKeyBase64;
    }

    /**
     * 푸시 서버에 보낼 Authorization 헤더: "vapid t=JWT, k=공개키".
     * JWT의 aud는 푸시 서버 주소의 origin (예: https://fcm.googleapis.com)
     */
    public String authorization(String endpoint) throws GeneralSecurityException {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        long exp = clock.instant().plus(TOKEN_TTL).getEpochSecond();

        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String claims = b64("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"" + subject + "\"}");
        String signingInput = header + "." + claims;

        // JWT의 ES256 서명은 DER이 아닌 r||s 64바이트 형식이다 (JDK의 P1363 형식이 바로 이것)
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(privateKey);
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String jwt = signingInput + "." + EcKeys.base64Url(signer.sign());
        return "vapid t=" + jwt + ", k=" + publicKeyBase64;
    }

    ECPublicKey ecPublicKey() {
        return publicKey;
    }

    private static String b64(String json) {
        return EcKeys.base64Url(json.getBytes(StandardCharsets.UTF_8));
    }
}
