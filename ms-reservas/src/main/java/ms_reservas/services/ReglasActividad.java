package ms_reservas.services;

import ms_reservas.clients.OpenMeteoClient.PronosticoDia;
import ms_reservas.dtos.external.ActividadCatalogoDTO;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

/**
 * Reglas de negocio puras (sin dependencias) sobre si una actividad puede realizarse.
 * Se usan al crear una reserva y al revalidarla.
 */
public final class ReglasActividad {

    /** RN02: probabilidad de lluvia a partir de la cual se rechaza (estrictamente mayor). */
    public static final int PROBABILIDAD_LLUVIA_MAX = 70;

    /** RN02: códigos WMO >= 61 son lluvia, nieve o tormenta. */
    public static final int WEATHER_CODE_LLUVIA = 61;

    private ReglasActividad() {
    }

    /** Edad cumplida al día de la excursión. */
    public static int edadAl(LocalDate fechaNacimiento, LocalDate fecha) {
        return Period.between(fechaNacimiento, fecha).getYears();
    }

    /**
     * Devuelve el motivo si la edad no está dentro del rango permitido por la actividad.
     */
    public static Optional<String> validarEdad(ActividadCatalogoDTO actividad, String nombre,
                                               LocalDate fechaNacimiento, LocalDate fechaExcursion) {
        int edad = edadAl(fechaNacimiento, fechaExcursion);
        if (actividad.edadMinima() != null && edad < actividad.edadMinima()) {
            return Optional.of(nombre + " tendrá " + edad + " años y la actividad exige una edad mínima de "
                    + actividad.edadMinima());
        }
        if (actividad.edadMaxima() != null && edad > actividad.edadMaxima()) {
            return Optional.of(nombre + " tendrá " + edad + " años y la actividad admite hasta "
                    + actividad.edadMaxima() + " años");
        }
        return Optional.empty();
    }

    /**
     * true si la fecha cae dentro de la temporada de la actividad. Soporta temporadas que cruzan el año
     * (ej. septiembre a abril). Sin temporada definida, la actividad se realiza todo el año.
     */
    public static boolean enTemporada(ActividadCatalogoDTO actividad, LocalDate fecha) {
        Integer inicio = actividad.mesInicioTemporada();
        Integer fin = actividad.mesFinTemporada();
        if (inicio == null || fin == null) {
            return true;
        }
        int mes = fecha.getMonthValue();
        return inicio <= fin ? (mes >= inicio && mes <= fin) : (mes >= inicio || mes <= fin);
    }

    /** RN02: el pronóstico impide realizar una actividad que requiere buen clima. */
    public static boolean climaAdverso(PronosticoDia p) {
        return p.probabilidadLluvia() > PROBABILIDAD_LLUVIA_MAX || p.weatherCode() >= WEATHER_CODE_LLUVIA;
    }

    public static String describirClima(PronosticoDia p) {
        return "Probabilidad de lluvia: " + p.probabilidadLluvia() + "%, código de clima: "
                + p.weatherCode() + ", temperatura máxima: " + p.temperaturaMax() + " °C";
    }
}
