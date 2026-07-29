package com.team02.mopl.global.config;

import com.team02.mopl.global.websocket.StompChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker // STOMP 사용 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompChannelInterceptor interceptor;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // 클라이언트가 연결할 WebSocket 엔드포인트 정의
    // JWT 인증은 StompChannelInterceptor의 CONNECT 처리에서 수행한다.
    registry
        .addEndpoint("/ws")
        .setAllowedOriginPatterns("*") // CORS 허용
        .withSockJS(); // SockJS fallback 지원
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    // 메시지 발행(Publish) 경로 prefix
    registry.setApplicationDestinationPrefixes("/pub");

    // 구독(Subscribe) 경로 prefix
    registry.enableSimpleBroker("/sub");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registry) {
    registry.interceptors(interceptor);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration.setMessageSizeLimit(2 * 1024 * 1024); // 2MB
    registration.setSendBufferSizeLimit(512 * 1024);
    registration.setSendTimeLimit(20_000);
  }
}
