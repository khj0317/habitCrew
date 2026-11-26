package com.habitcrew.support;

import com.habitcrew.domain.push.PushGateway;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** 테스트용: 푸시 서버로 보내지 않고 보낸 내용을 기록한다. 응답 코드도 바꿔 볼 수 있다 */
public class RecordingPushGateway implements PushGateway {

    public record Sent(String endpoint, byte[] body, Map<String, String> headers) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile int status = 201;

    @Override
    public int send(String endpoint, byte[] body, Map<String, String> headers) {
        sent.add(new Sent(endpoint, body, headers));
        return status;
    }

    public List<Sent> sent() {
        return sent;
    }

    public List<Sent> sentTo(String endpoint) {
        return sent.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
    }

    public void respondWith(int status) {
        this.status = status;
    }

    public void reset() {
        sent.clear();
        status = 201;
    }
}
