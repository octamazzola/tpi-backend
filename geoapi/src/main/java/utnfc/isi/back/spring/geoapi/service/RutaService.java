package utnfc.isi.back.spring.geoapi.service;

import utnfc.isi.back.spring.geoapi.dto.OsrmStatusDTO;
import utnfc.isi.back.spring.geoapi.dto.RutaRequest;
import utnfc.isi.back.spring.geoapi.dto.RutaResponse;

/**
 * Interfaz de servicio para operaciones geoespaciales y de ruteo con OSRM.
 * Cumple con el principio de Separated Interface (SOLID - DIP).
 */
public interface RutaService {

    /**
     * Calcula la ruta terrestre exacta entre origen y destino recibiendo el payload completo.
     */
    RutaResponse calcularRuta(RutaRequest request);

    /**
     * Estimación ágil de ruta mediante parámetros primitivos de coordenadas.
     */
    RutaResponse estimarRuta(double latOrig, double lonOrig, double latDest, double lonDest);

    /**
     * Verifica la disponibilidad y conectividad del motor OSRM local.
     */
    OsrmStatusDTO checkOsrmHealth();
}
