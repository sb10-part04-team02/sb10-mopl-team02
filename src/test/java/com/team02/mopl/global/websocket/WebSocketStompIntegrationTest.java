package com.team02.mopl.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.contentchat.dto.ContentChatDto;
import com.team02.mopl.domain.contentchat.dto.ContentChatSendRequest;
import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import com.team02.mopl.domain.dm.repository.ConversationRepository;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.domain.watching.entity.WatchingSession;
import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import com.team02.mopl.support.TestcontainersConfiguration;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * WebSocket(STOMP) 통합 테스트.
 *
 * <p>실제 서버 포트로 STOMP 클라이언트를 연결해 CONNECT 인증(JwtRegistry가 Redis를 사용하므로 Redis Testcontainer 필요), 콘텐츠
 * 채팅 브로드캐스트/시청 세션 검증, DM 구독 멤버십 검증을 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, WebSocketStompIntegrationTest.RedisTestConfig.class})
class WebSocketStompIntegrationTest {

  @TestConfiguration(proxyBeanMethods = false)
  static class RedisTestConfig {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
      return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }
  }

  @LocalServerPort private int port;

  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private ContentRepository contentRepository;
  @Autowired private WatchingSessionRepository watchingSessionRepository;
  @Autowired private ConversationRepository conversationRepository;
  @Autowired private ConversationMemberRepository conversationMemberRepository;
  @Autowired private SimpMessagingTemplate messagingTemplate;
  @Autowired private ObjectMapper objectMapper;

  private WebSocketStompClient stompClient;

  @BeforeEach
  void setUpClient() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    MappingJackson2MessageConverter jacksonConverter = new MappingJackson2MessageConverter();
    jacksonConverter.setObjectMapper(objectMapper);
    // 서버가 String 페이로드를 text/plain으로 보내므로 String 컨버터를 함께 둔다.
    stompClient.setMessageConverter(
        new CompositeMessageConverter(List.of(new StringMessageConverter(), jacksonConverter)));
  }

  @AfterEach
  void tearDownClient() {
    stompClient.stop();
  }

  @Test
  @DisplayName("유효한 액세스 토큰으로 CONNECT하면 STOMP 세션이 연결된다")
  void connect_withValidToken_succeeds() throws Exception {
    User user = saveUser("연결유저");

    StompSession session = connect(accessTokenFor(user), new CapturingSessionHandler());

    assertThat(session.isConnected()).isTrue();
    session.disconnect();
  }

  @Test
  @DisplayName("Authorization 헤더 없이 CONNECT하면 연결이 거부된다")
  void connect_withoutToken_isRejected() {
    assertThatThrownBy(() -> connect(null, new CapturingSessionHandler()))
        .isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("유효하지 않은 토큰으로 CONNECT하면 연결이 거부된다")
  void connect_withInvalidToken_isRejected() {
    assertThatThrownBy(() -> connect("invalid-token", new CapturingSessionHandler()))
        .isInstanceOf(Exception.class);
  }

  @Test
  @DisplayName("활성 시청 세션 참여자가 보낸 콘텐츠 채팅이 구독자에게 브로드캐스트된다")
  void contentChat_watcherMessage_isBroadcast() throws Exception {
    User watcher = saveUser("시청자");
    Content content = saveContent();
    watchingSessionRepository.save(new WatchingSession(content, watcher, Instant.now(), null));

    StompSession session = connect(accessTokenFor(watcher), new CapturingSessionHandler());
    BlockingQueue<ContentChatDto> received = new LinkedBlockingQueue<>();
    session.subscribe(
        "/sub/contents/" + content.getId() + "/chat",
        new QueueingFrameHandler<>(ContentChatDto.class, received));
    awaitSubscriptionRegistered();

    session.send("/pub/contents/" + content.getId() + "/chat", new ContentChatSendRequest("안녕하세요"));

    ContentChatDto message = received.poll(10, TimeUnit.SECONDS);
    assertThat(message).isNotNull();
    assertThat(message.content()).isEqualTo("안녕하세요");
    assertThat(message.sender().userId()).isEqualTo(watcher.getId());
    session.disconnect();
  }

  @Test
  @DisplayName("시청 세션에 참여하지 않은 발신자의 콘텐츠 채팅은 브로드캐스트되지 않고 연결은 유지된다")
  void contentChat_nonWatcherMessage_isRejected() throws Exception {
    User nonWatcher = saveUser("미시청자");
    Content content = saveContent();

    StompSession session = connect(accessTokenFor(nonWatcher), new CapturingSessionHandler());
    BlockingQueue<ContentChatDto> received = new LinkedBlockingQueue<>();
    session.subscribe(
        "/sub/contents/" + content.getId() + "/chat",
        new QueueingFrameHandler<>(ContentChatDto.class, received));
    awaitSubscriptionRegistered();

    session.send("/pub/contents/" + content.getId() + "/chat", new ContentChatSendRequest("안녕하세요"));

    // NotWatchingContentException은 @MessageExceptionHandler가 흡수하므로 브로드캐스트만 차단되고 연결은 살아있다.
    assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();
    assertThat(session.isConnected()).isTrue();
    session.disconnect();
  }

  @Test
  @DisplayName("대화방 멤버는 DM 구독이 허용되고 메시지를 수신한다")
  void dmSubscribe_member_isAllowed() throws Exception {
    User member = saveUser("멤버");
    User other = saveUser("상대");
    Conversation conversation = createConversation(member, other);

    StompSession session = connect(accessTokenFor(member), new CapturingSessionHandler());
    String destination = "/sub/conversations/" + conversation.getId() + "/direct-messages";
    BlockingQueue<String> received = new LinkedBlockingQueue<>();
    session.subscribe(destination, new QueueingFrameHandler<>(String.class, received));

    // 구독이 실제로 등록됐는지 서버 측 브로커로 직접 전송해 수신을 확인한다.
    awaitSubscriptionRegistered();
    messagingTemplate.convertAndSend(destination, "ping");

    assertThat(received.poll(10, TimeUnit.SECONDS)).isEqualTo("ping");
    session.disconnect();
  }

  @Test
  @DisplayName("대화방 비멤버가 DM을 구독하면 ERROR 프레임으로 차단된다")
  void dmSubscribe_nonMember_isBlocked() throws Exception {
    User memberA = saveUser("멤버A");
    User memberB = saveUser("멤버B");
    User outsider = saveUser("비멤버");
    Conversation conversation = createConversation(memberA, memberB);

    CapturingSessionHandler handler = new CapturingSessionHandler();
    StompSession session = connect(accessTokenFor(outsider), handler);
    session.subscribe(
        "/sub/conversations/" + conversation.getId() + "/direct-messages",
        new QueueingFrameHandler<>(String.class, new LinkedBlockingQueue<>()));

    String errorFrame = handler.errorFrames.poll(10, TimeUnit.SECONDS);
    assertThat(errorFrame).isNotNull();
  }

  private User saveUser(String name) {
    String email = name + "-" + UUID.randomUUID() + "@test.com";
    return userRepository.save(new User(name, email, "password123!", null, Role.USER, false));
  }

  private Content saveContent() {
    return contentRepository.save(
        new Content(ContentType.MOVIE, "테스트 콘텐츠", "설명", "https://example.com/thumb.png"));
  }

  private Conversation createConversation(User userA, User userB) {
    Conversation conversation = conversationRepository.save(new Conversation());
    conversationMemberRepository.save(
        ConversationMember.builder()
            .conversation(conversation)
            .user(userA)
            .lastReadAt(Instant.now())
            .build());
    conversationMemberRepository.save(
        ConversationMember.builder()
            .conversation(conversation)
            .user(userB)
            .lastReadAt(Instant.now())
            .build());
    return conversation;
  }

  private String accessTokenFor(User user) {
    UserDto userDto =
        new UserDto(
            user.getId(),
            user.getCreatedAt(),
            user.getEmail(),
            user.getName(),
            user.getProfileImageUrl(),
            user.getRole(),
            false);
    return jwtTokenProvider.generateAccessToken(new MoplUserDetails(userDto, user.getPassword()));
  }

  private StompSession connect(String accessToken, StompSessionHandlerAdapter handler)
      throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    if (accessToken != null) {
      connectHeaders.add("Authorization", "Bearer " + accessToken);
    }
    String url = "ws://localhost:" + port + "/ws/websocket";
    return stompClient
        .connectAsync(url, new WebSocketHttpHeaders(), connectHeaders, handler)
        .get(10, TimeUnit.SECONDS);
  }

  // 심플 브로커(enableSimpleBroker)는 SUBSCRIBE에 RECEIPT를 보내지 않아(DISCONNECT만 지원)
  // 등록 시점을 클라이언트가 알 수 없으므로 짧게 대기한다.
  private void awaitSubscriptionRegistered() throws InterruptedException {
    Thread.sleep(500);
  }

  /** 구독 페이로드를 타입 변환해 큐에 적재하는 프레임 핸들러. */
  private static class QueueingFrameHandler<T> implements StompFrameHandler {

    private final Class<T> payloadType;
    private final BlockingQueue<T> queue;

    private QueueingFrameHandler(Class<T> payloadType, BlockingQueue<T> queue) {
      this.payloadType = payloadType;
      this.queue = queue;
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return payloadType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleFrame(StompHeaders headers, Object payload) {
      queue.offer((T) payload);
    }
  }

  /** 서버가 보낸 ERROR 프레임의 message 헤더를 수집하는 세션 핸들러. */
  private static class CapturingSessionHandler extends StompSessionHandlerAdapter {

    private final BlockingQueue<String> errorFrames = new LinkedBlockingQueue<>();

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      String message = headers.getFirst("message");
      errorFrames.offer(message == null ? "(no message header)" : message);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
      errorFrames.offer("transport-error: " + exception.getMessage());
    }
  }
}
