// src/main/java/com/example/dispatcheranalyze/config/WebSocketConfig.java
package com.example.dispatcheranalyze.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry r) {
        // ws://host/ws  (+ SockJS fallback)
        r.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry cfg) {
        cfg.enableSimpleBroker("/topic");          // сервер → клиент
        cfg.setApplicationDestinationPrefixes("/app");
    }
}
