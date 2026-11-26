package com.habitcrew.domain.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class HttpPushGateway implements PushGateway {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER) // 허용한 푸시 서버 밖으로 끌려가지 않게
            .build();

    @Override
    public int send(String endpoint, byte[] body, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("푸시 서버 응답 {}: {}", response.statusCode(), response.body());
            }
            return response.statusCode();
        } catch (IOException e) {
            log.warn("푸시 전송 실패: {}", e.getMessage());
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }
}
