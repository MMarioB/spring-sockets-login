package com.discobingohits.login_sockets_bingo.config;

import com.discobingohits.login_sockets_bingo.handler.GameWebSocketHandler;
import com.discobingohits.login_sockets_bingo.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {
    private final WebSocketAuthInterceptor authInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new GameWebSocketHandler(), "/socket")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("http://localhost:8081")
                .withSockJS();
    }
}
