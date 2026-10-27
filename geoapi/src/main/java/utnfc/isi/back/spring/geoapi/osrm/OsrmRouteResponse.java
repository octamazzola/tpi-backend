package utnfc.isi.back.spring.geoapi.osrm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OsrmRouteResponse(String code, List<Route> routes) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(double distance, double duration, List<Leg> legs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(double distance, double duration) {}
}
