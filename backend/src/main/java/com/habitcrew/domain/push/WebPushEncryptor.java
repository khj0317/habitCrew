package com.habitcrew.domain.push;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * 웹 푸시 메시지 암호화 (RFC 8291 + RFC 8188 aes128gcm).
 * 푸시 서버(구글·모질라·애플)는 내용을 볼 수 없고, 구독한 브라우저만 풀 수 있다.
 *
 * <pre>
 * 1. 메시지마다 새 키 쌍(as)을 만들고, 브라우저 공개 키(ua)와 ECDH로 공유 비밀을 구한다
 * 2. 브라우저가 준 auth 비밀값과 HKDF로 섞어 IKM을 만든다
 * 3. 무작위 salt로 다시 HKDF 해서 AES-128 키(CEK)와 nonce를 뽑는다
 * 4. 본문 + 구분 바이트(0x02)를 AES-128-GCM으로 암호화한다
 * 5. 헤더(salt, 레코드 크기, 내 공개 키) + 암호문을 보낸다
 * </pre>
 * 외부 라이브러리 없이 JDK 암호 기능만 쓴다.
 */
@Component
public class WebPushEncryptor {

    private static final int RECORD_SIZE = 4096;
    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private final SecureRandom random = new SecureRandom();

    public byte[] encrypt(byte[] payload, String p256dh, String auth) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return encrypt(payload, EcKeys.fromBase64Url(p256dh), EcKeys.fromBase64Url(auth), EcKeys.generate(), salt);
    }

    /** 테스트에서 salt와 키를 고정해 결과를 재현할 수 있게 열어 둔다 */
    byte[] encrypt(byte[] payload, byte[] uaPublicRaw, byte[] authSecret, KeyPair asKeys, byte[] salt)
            throws GeneralSecurityException {
        if (payload.length > RECORD_SIZE - 17 - 86) {
            throw new IllegalArgumentException("푸시 메시지가 너무 깁니다.");
        }
        ECPublicKey uaPublic = EcKeys.publicFromRaw(uaPublicRaw);
        byte[] asPublicRaw = EcKeys.rawPublic((ECPublicKey) asKeys.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(asKeys.getPrivate());
        agreement.doPhase(uaPublic, true);
        byte[] ecdhSecret = agreement.generateSecret();

        // IKM = HKDF(salt=auth, ikm=ecdh, info="WebPush: info\0" || ua_public || as_public, L=32)
        byte[] keyInfo = concat(KEY_INFO_PREFIX, uaPublicRaw, asPublicRaw);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

        byte[] cek = hkdf(salt, ikm, CEK_INFO, 16);
        byte[] nonce = hkdf(salt, ikm, NONCE_INFO, 12);

        byte[] plaintext = Arrays.copyOf(payload, payload.length + 1);
        plaintext[payload.length] = 0x02; // 마지막(이자 유일한) 레코드라는 표시

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublicRaw.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublicRaw.length).put(asPublicRaw);
        return concat(header.array(), ciphertext);
    }

    /** HKDF-SHA256 (RFC 5869). 32바이트 이하만 필요하므로 Expand는 한 블록으로 충분하다 */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        byte[] okm = hmac(prk, concat(info, new byte[]{0x01}));
        return Arrays.copyOf(okm, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
