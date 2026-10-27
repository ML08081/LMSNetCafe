package com.lms.netcafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.netcafe.module.ai.service.AiModelClient;
import com.lms.netcafe.module.ai.service.AiRequestLimiter;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class AiModelClientTests {
    ObjectMapper json=new ObjectMapper();

    @Test void compatibleGatewaySendsBoundedMessagesAndReturnsModelResponse() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> body=new AtomicReference<>(), authorization=new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] result="{\"choices\":[{\"message\":{\"content\":\"根据快照回答\"},\"finish_reason\":\"stop\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,result.length);exchange.getResponseBody().write(result);exchange.close();
        });server.start();
        try {
            AiModelClient client=new AiModelClient(settings(server.getAddress().getPort()),json);
            var response=client.complete("规则",Map.of("balance",100),List.of(Map.of("role","user","content","上一个问题")),"余额？","基础答案");
            assertThat(response.mode()).isEqualTo("MODEL");assertThat(response.text()).isEqualTo("根据快照回答");
            assertThat(authorization.get()).isEqualTo("Bearer test-secret");
            var request=json.readTree(body.get());
            assertThat(request.path("model").asText()).isEqualTo("test-model");
            assertThat(request.path("stream").asBoolean()).isFalse();
            assertThat(request.path("messages").size()).isEqualTo(3);
            assertThat(request.path("max_completion_tokens").asInt()).isEqualTo(800);
            assertThat(json.writeValueAsString(client.status())).doesNotContain("test-secret","baseUrl");
        } finally { server.stop(0); }
    }

    @Test void providerFailureAndInvalidOrTruncatedOutputUseExplicitFallback() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> payload=new AtomicReference<>("PRIVATE_PROVIDER_DETAIL test-secret");
        server.createContext("/v1/chat/completions",exchange -> {
            byte[] bytes=payload.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(payload.get().startsWith("PRIVATE")?401:200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            AiModelClient client=new AiModelClient(settings(server.getAddress().getPort()),json);
            for(String value:List.of("PRIVATE_PROVIDER_DETAIL test-secret","invalid JSON","{\"choices\":[{\"message\":{\"content\":\"未完\"},\"finish_reason\":\"length\"}]}")) {
                payload.set(value);
                var result=client.complete("规则",Map.of(),List.of(),"问题","真实基础结果");
                assertThat(result.mode()).isEqualTo("BUILTIN");assertThat(result.text()).isEqualTo("真实基础结果");
                assertThat(result.notice()).doesNotContain("test-secret","PRIVATE_PROVIDER_DETAIL");
            }
        } finally { server.stop(0); }
    }

    @Test void disabledModelDoesNotCallNetworkAndRequestLimitIsEnforced() {
        AiModelClient client=new AiModelClient(new AiModelClient.Settings(false,"http://127.0.0.1:1/v1","","",1,800,"max_tokens"),json);
        assertThat(client.complete("规则",Map.of(),List.of(),"问题","基础结果").mode()).isEqualTo("BUILTIN");
        AiRequestLimiter limiter=new AiRequestLimiter();
        for(int i=0;i<10;i++)limiter.check(1L);
        assertThatThrownBy(() -> limiter.check(1L)).isInstanceOf(ResponseStatusException.class);
        assertThatCode(() -> limiter.check(2L)).doesNotThrowAnyException();
    }
    AiModelClient.Settings settings(int port) { return new AiModelClient.Settings(true,"http://127.0.0.1:"+port+"/v1","test-secret","test-model",2,800,"max_completion_tokens"); }
}
