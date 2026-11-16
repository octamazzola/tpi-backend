package ms_reservas.services;

import ms_reservas.clients.OpenMeteoClient.PronosticoDia;
import ms_reservas.dtos.external.ActividadCatalogoDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasActividadTest {

    private static ActividadCatalogoDTO actividad(Integer edadMin, Integer edadMax, Integer mesIni, Integer mesFin) {
        return new ActividadCatalogoDTO(1L, "Test", 1000.0, 10, -31.0, -64.0, "Córdoba", true,
                edadMin, edadMax, mesIni, mesFin, true);
    }

    @Test
    void edadSeCalculaAlDiaDeLaExcursion() {
        // Cumple 18 el 2026-12-01: el día anterior todavía tiene 17
        LocalDate nacimiento = LocalDate.of(2008, 12, 1);
        assertThat(ReglasActividad.edadAl(nacimiento, LocalDate.of(2026, 11, 30))).isEqualTo(17);
        assertThat(ReglasActividad.edadAl(nacimiento, LocalDate.of(2026, 12, 1))).isEqualTo(18);
    }

    @Test
    void validaEdadMinimaYMaxima() {
        ActividadCatalogoDTO a = actividad(14, 65, null, null);
        LocalDate fecha = LocalDate.of(2026, 12, 10);
        assertThat(ReglasActividad.validarEdad(a, "Niño", LocalDate.of(2015, 1, 1), fecha)).isPresent();
        assertThat(ReglasActividad.validarEdad(a, "Mayor", LocalDate.of(1950, 1, 1), fecha)).isPresent();
        assertThat(ReglasActividad.validarEdad(a, "Adulto", LocalDate.of(1990, 1, 1), fecha)).isEmpty();
    }

    @Test
    void temporadaQueCruzaElAnio() {
        ActividadCatalogoDTO septiembreAAbril = actividad(null, null, 9, 4);
        assertThat(ReglasActividad.enTemporada(septiembreAAbril, LocalDate.of(2026, 12, 15))).isTrue();
        assertThat(ReglasActividad.enTemporada(septiembreAAbril, LocalDate.of(2027, 3, 15))).isTrue();
        assertThat(ReglasActividad.enTemporada(septiembreAAbril, LocalDate.of(2027, 6, 15))).isFalse();
    }

    @Test
    void temporadaDentroDelMismoAnioYSinTemporada() {
        ActividadCatalogoDTO marzoANoviembre = actividad(null, null, 3, 11);
        assertThat(ReglasActividad.enTemporada(marzoANoviembre, LocalDate.of(2026, 11, 30))).isTrue();
        assertThat(ReglasActividad.enTemporada(marzoANoviembre, LocalDate.of(2026, 12, 1))).isFalse();
        assertThat(ReglasActividad.enTemporada(actividad(null, null, null, null), LocalDate.of(2026, 7, 1))).isTrue();
    }

    @Test
    void climaAdversoPorLluviaOCodigo() {
        assertThat(ReglasActividad.climaAdverso(new PronosticoDia(71, 1, 20.0))).isTrue();
        assertThat(ReglasActividad.climaAdverso(new PronosticoDia(70, 1, 20.0))).isFalse();
        assertThat(ReglasActividad.climaAdverso(new PronosticoDia(10, 63, 20.0))).isTrue();
    }
}
