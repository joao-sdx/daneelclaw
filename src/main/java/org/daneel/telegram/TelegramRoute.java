package org.daneel.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.TelegramConstants;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.apache.camel.component.telegram.model.OutgoingTextMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "daneel.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TelegramRoute extends RouteBuilder {

  private final TelegramProperties properties;
  private final TelegramRunner runner;

  @Override
  public void configure() {
    onException(Exception.class)
        .handled(true)
        .logHandled(true)
        .logStackTrace(true)
        .log(LoggingLevel.ERROR, "telegram_route_error: ${exception.message}");

    from("telegram:bots?authorizationToken={{daneel.telegram.bot-token}}"
            + "&delay={{daneel.telegram.poll-delay-ms}}")
        .routeId("telegram-inbound")
        .filter(this::isAllowedTextMessage)
        .process(
            exchange -> {
              var chatId =
                  exchange.getIn().getHeader(TelegramConstants.TELEGRAM_CHAT_ID, Long.class);
              var text = exchange.getIn().getBody(IncomingMessage.class).getText();
              exchange
                  .getIn()
                  .setHeader(TelegramConstants.TELEGRAM_CHAT_ID, String.valueOf(chatId));
              exchange.getIn().setBody(runner.handle(chatId, text));
            })
        .split(body())
        .process(
            exchange -> {
              var msg = new OutgoingTextMessage();
              msg.setText(exchange.getIn().getBody(String.class));
              exchange.getIn().setBody(msg);
            })
        .to("telegram:bots?authorizationToken={{daneel.telegram.bot-token}}");
  }

  private boolean isAllowedTextMessage(Exchange exchange) {
    var body = exchange.getIn().getBody(IncomingMessage.class);
    if (body == null || body.getText() == null || body.getText().isBlank()) {
      return false;
    }
    var allowed = properties.getAllowedChatIds();
    if (allowed.isEmpty()) {
      return true;
    }
    var chatId = exchange.getIn().getHeader(TelegramConstants.TELEGRAM_CHAT_ID, Long.class);
    if (chatId == null) {
      return false;
    }
    if (!allowed.contains(chatId)) {
      log.warn("telegram_unauthorized chatId={}", chatId);
      return false;
    }
    return true;
  }
}
