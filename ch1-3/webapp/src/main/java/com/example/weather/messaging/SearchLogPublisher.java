package com.example.weather.messaging;

import com.example.weather.config.RabbitConfig;
import com.example.weather.dto.SearchLogMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchLogPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String city) {
        SearchLogMessage msg = SearchLogMessage.builder()
                .city(city)
                .timestamp(Instant.now())
                .build();
        rabbitTemplate.convertAndSend(RabbitConfig.SEARCH_LOG_QUEUE, msg);
        log.info("[Publisher] Sent search log for city={} at {}", msg.getCity(), msg.getTimestamp());
    }
}
