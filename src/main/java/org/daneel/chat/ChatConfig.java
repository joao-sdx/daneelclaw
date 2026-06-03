package org.daneel.chat;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
class ChatConfig {

  private static final String SUMMARY_SYSTEM =
      """
            Tu es un outil de résumé de conversation. Résume l'échange suivant de façon
            concise en français, en préservant les faits, décisions, préférences et le
            contexte importants pour la suite. Ne réponds qu'avec le résumé, sans préambule.
            """;

  @Bean
  ChatClient chatClient(
      ChatClient.Builder builder, @Value("classpath:system-prompt.md") Resource systemPrompt) {
    return builder.defaultSystem(systemPrompt).build();
  }

  @Bean
  ChatClient toolSelectorChatClient(ChatClient.Builder builder) {
    return builder.defaultToolCallbacks(new ToolCallback[0]).build();
  }

  @Bean
  ChatClient summaryChatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem(SUMMARY_SYSTEM)
        .defaultToolCallbacks(new ToolCallback[0]) // explicit: no tools during summarization
        .build();
  }

  @Bean
  Semaphore lmStudioSemaphore() {
    return new Semaphore(1, true);
  }

  @Bean
  RestClientCustomizer http11RestClientCustomizer(
      Semaphore lmStudioSemaphore,
      @Value("${daneel.llm.serialize-calls:true}") boolean serializeCalls) {
    var httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    var factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofMinutes(2));
    return builder -> {
      builder.requestFactory(factory);
      if (serializeCalls) {
        builder.requestInterceptor(new SerializingClientHttpRequestInterceptor(lmStudioSemaphore));
      }
    };
  }
}
