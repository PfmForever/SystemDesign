package com.example.weather.messaging;

import com.example.weather.config.RabbitConfig;
import com.example.weather.dto.SearchLogMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SearchLogConsumer {

    @RabbitListener(queues = RabbitConfig.SEARCH_LOG_QUEUE)
    public void onMessage(SearchLogMessage msg) throws InterruptedException {
        log.info("[Consumer] Received search log: city={} at {}", msg.getCity(), msg.getTimestamp());

        // 模擬耗時任務: 更新統計、寫入 analytics 儲存等
        Thread.sleep(2000);

        log.info("[Consumer] 非同步更新 [{}] 天氣統計分析完成", msg.getCity());
    }
}
