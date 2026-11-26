package com.habitcrew.domain.push;

import java.util.Map;

/**
 * 푸시 서버로 암호화된 메시지를 실제로 보내는 부분. 테스트에서는 보내지 않고 기록만 하는 구현으로 바꿔 끼운다.
 *
 * @return HTTP 상태 코드 (201: 접수됨, 404/410: 구독이 사라짐)
 */
public interface PushGateway {

    int send(String endpoint, byte[] body, Map<String, String> headers);
}
