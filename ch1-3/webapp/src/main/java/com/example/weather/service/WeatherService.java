package com.example.weather.service;

import com.example.weather.client.ExternalWeatherClient;
import com.example.weather.dto.DailyForecast;
import com.example.weather.dto.WeatherResponse;
import com.example.weather.entity.WeatherForecast;
import com.example.weather.repository.WeatherForecastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-Through Cache:
 *   1) Cache Hit  -> 直接回傳
 *   2) Cache Miss -> 讀 DB (Slave); 若 DB 無資料 -> 打第三方 API -> 寫入 DB (Master) -> 寫入 Cache
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final Duration TTL = Duration.ofHours(1);
    private static final String CACHE_PREFIX = "weather:";

    private final WeatherForecastRepository repository;
    private final ExternalWeatherClient externalWeatherClient;
    private final RedisTemplate<String, WeatherResponse> redisTemplate;

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    public WeatherResponse getWeekForecast(String city) {
        String key       = CACHE_PREFIX + city.toLowerCase();
        String hostname  = instanceId;

        // 1) Cache lookup
        WeatherResponse cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.info("[Cache HIT ] city={}", city);
            cached.setCache("HIT");
            cached.setServedBy(hostname);
            return cached;
        }
        log.info("[Cache MISS] city={}", city);

        // 2) 讀 Slave DB
        List<DailyForecast> forecasts = readFromSlave(city);

        // 3) DB miss -> 打第三方 API 並寫入 Master + Cache
        if (forecasts.isEmpty()) {
            log.info("[DB   MISS] city={} -> fetch external API", city);
            forecasts = externalWeatherClient.fetchWeekForecast(city);
            persistToMaster(city, forecasts);
        } else {
            log.info("[DB   HIT ] city={} rows={}", city, forecasts.size());
        }

        WeatherResponse response = WeatherResponse.builder()
                .city(city)
                .forecasts(forecasts)
                .cache("MISS")
                .servedBy(hostname)
                .build();

        redisTemplate.opsForValue().set(key, response, TTL);
        return response;
    }

    @Transactional(readOnly = true)
    protected List<DailyForecast> readFromSlave(String city) {
        LocalDate today = LocalDate.now();
        return repository
                .findByCityAndForecastDateBetweenOrderByForecastDateAsc(city, today, today.plusDays(6))
                .stream()
                .map(e -> DailyForecast.builder()
                        .date(e.getForecastDate())
                        .temperature(e.getTemperature())
                        .description(e.getDescription())
                        .build())
                .toList();
    }

    @Transactional
    protected void persistToMaster(String city, List<DailyForecast> forecasts) {
        List<WeatherForecast> entities = forecasts.stream()
                .map(f -> WeatherForecast.builder()
                        .city(city)
                        .forecastDate(f.getDate())
                        .temperature(f.getTemperature())
                        .description(f.getDescription())
                        .build())
                .toList();
        repository.saveAll(entities);
        log.info("[DB WRITE] city={} rows={}", city, entities.size());
    }
}
