package com.habitcrew.domain.push;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * P-256 타원곡선 키를 웹 푸시에서 쓰는 형식으로 바꾼다.
 * 브라우저와 푸시 표준은 공개 키를 "0x04 + x(32바이트) + y(32바이트)" 65바이트로 주고받는다.
 */
public final class EcKeys {

    public static final ECParameterSpec P256;

    static {
        try {
            P256 = ((ECPublicKey) generate().getPublic()).getParams();
        } catch (GeneralSecurityException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private EcKeys() {
    }

    public static KeyPair generate() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    public static byte[] rawPublic(ECPublicKey key) {
        byte[] raw = new byte[65];
        raw[0] = 0x04;
        System.arraycopy(fixed32(key.getW().getAffineX()), 0, raw, 1, 32);
        System.arraycopy(fixed32(key.getW().getAffineY()), 0, raw, 33, 32);
        return raw;
    }

    public static ECPublicKey publicFromRaw(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 65 || raw[0] != 0x04) {
            throw new GeneralSecurityException("P-256 공개 키 형식이 아닙니다.");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    public static byte[] rawPrivate(ECPrivateKey key) {
        return fixed32(key.getS());
    }

    public static ECPrivateKey privateFromRaw(byte[] d) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
    }

    public static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 브라우저는 패딩 없는 base64url을 주지만, 패딩이 있거나 일반 base64여도 받아 준다 */
    public static byte[] fromBase64Url(String value) {
        String normalized = value.trim().replace('+', '-').replace('/', '_').replace("=", "");
        return Base64.getUrlDecoder().decode(normalized);
    }

    /** BigInteger는 앞에 0이 붙거나 빠질 수 있어서 항상 32바이트로 맞춘다 */
    private static byte[] fixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, out, 32 - copy, copy);
        return out;
    }
}
