package utnfc.isi.back.spring.geoapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "osrm")
public record OsrmProperties(String baseUrl) {
    public OsrmProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:5000";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
    }
}
