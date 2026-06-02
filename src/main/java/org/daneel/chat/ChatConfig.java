package org.daneel.chat;

import org.daneel.tool.ToolRegistrar;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
class ChatConfig {

    private static final String SUMMARY_SYSTEM = """
            Tu es un outil de résumé de conversation. Résume l'échange suivant de façon
            concise en français, en préservant les faits, décisions, préférences et le
            contexte importants pour la suite. Ne réponds qu'avec le résumé, sans préambule.
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
                          @Value("classpath:system-prompt.md") Resource systemPrompt,
                          ToolRegistrar toolRegistrar) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(toolRegistrar.getCallbacks())
                .build();
    }

    @Bean
    ChatClient summaryChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SUMMARY_SYSTEM)
                .defaultToolCallbacks(new ToolCallback[0])   // explicit: no tools during summarization
                .build();
    }

    @Bean
    RestClientCustomizer http11RestClientCustomizer() {
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(2));
        return builder -> builder.requestFactory(factory);
    }
}
