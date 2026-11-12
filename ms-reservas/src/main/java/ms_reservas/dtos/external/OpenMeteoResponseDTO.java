package ms_reservas.dtos.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Respuesta de Open-Meteo: pronóstico diario (un valor por fecha en {@code time}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoResponseDTO(Daily daily) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(
        List<String> time,
        @JsonProperty("weather_code") List<Integer> weatherCode,
        @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax,
        @JsonProperty("temperature_2m_max") List<Double> temperature2mMax
    ) {}
}
