-- Master 初始化: 建立 weather_forecast 表格
-- Bitnami postgresql image 會在首次啟動時以 POSTGRESQL_USERNAME/POSTGRESQL_DATABASE 執行此腳本

CREATE TABLE IF NOT EXISTS weather_forecast (
    id           BIGSERIAL PRIMARY KEY,
    city         VARCHAR(100)   NOT NULL,
    forecast_date DATE          NOT NULL,
    temperature  NUMERIC(5,2)   NOT NULL,
    description  VARCHAR(255),
    updated_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_city_date UNIQUE (city, forecast_date)
);

CREATE INDEX IF NOT EXISTS idx_weather_city         ON weather_forecast (city);
CREATE INDEX IF NOT EXISTS idx_weather_forecast_dt  ON weather_forecast (forecast_date);
