package com.travelassistant.realtime.provider;
import com.travelassistant.realtime.api.RealtimeDtos.WeatherDay; import java.math.BigDecimal; import java.time.LocalDate; import java.util.List;
public interface WeatherDataProvider { Result forecast(BigDecimal latitude,BigDecimal longitude,String timezone,LocalDate start,LocalDate end); record Result(List<WeatherDay> days,String sourceUpdatedAt){} }
