package com.example.weather.repository;

import com.example.weather.entity.WeatherForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {

    List<WeatherForecast> findByCityAndForecastDateBetweenOrderByForecastDateAsc(
            String city, LocalDate from, LocalDate to);
}
