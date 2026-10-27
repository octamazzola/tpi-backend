package utnfc.isi.back.spring.geoapi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OsrmProperties.class)
public class RestClientConfig {
    @Bean
    RestClient osrmRestClient(OsrmProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
