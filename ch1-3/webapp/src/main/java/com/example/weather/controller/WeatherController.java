package com.example.weather.controller;

import com.example.weather.dto.WeatherResponse;
import com.example.weather.messaging.SearchLogPublisher;
import com.example.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final SearchLogPublisher searchLogPublisher;

    @GetMapping("/{city}")
    public WeatherResponse getWeather(@PathVariable String city) {
        // 非同步記錄搜尋日誌 (解耦 - 不阻塞回應)
        searchLogPublisher.publish(city);
        return weatherService.getWeekForecast(city);
    }
}
