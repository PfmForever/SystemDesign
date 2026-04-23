package com.example.weather.client;

import com.example.weather.dto.DailyForecast;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 第三方天氣 API 的 Mock：回傳未來 7 天的天氣。
 * 實際專案可替換為 OpenWeatherMap / CWA 等 API 客戶端。
 */
@Component
public class ExternalWeatherClient {

    private static final String[] DESCRIPTIONS = {
            "Sunny", "Cloudy", "Rainy", "Partly Cloudy", "Thunderstorm", "Windy", "Foggy"
    };

    private final Random random = new Random();

    public List<DailyForecast> fetchWeekForecast(String city) {
        List<DailyForecast> result = new ArrayList<>(7);
        LocalDate today = LocalDate.now();
        int cityHash = Math.abs(city.toLowerCase().hashCode());

        for (int i = 0; i < 7; i++) {
            double baseTemp = 18 + (cityHash % 10);
            double delta    = (random.nextDouble() * 10) - 5;
            BigDecimal temp = BigDecimal.valueOf(baseTemp + delta)
                                        .setScale(2, RoundingMode.HALF_UP);

            result.add(DailyForecast.builder()
                    .date(today.plusDays(i))
                    .temperature(temp)
                    .description(DESCRIPTIONS[(cityHash + i) % DESCRIPTIONS.length])
                    .build());
        }
        return result;
    }
}
