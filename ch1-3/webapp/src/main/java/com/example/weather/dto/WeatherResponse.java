package com.example.weather.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherResponse implements Serializable {

    private String city;

    private List<DailyForecast> forecasts;

    /** "HIT" / "MISS" — 方便在 Demo 時觀察 Cache 表現 */
    private String cache;

    /** 處理此請求的容器 hostname，用於驗證 Load Balancing */
    private String servedBy;
}
