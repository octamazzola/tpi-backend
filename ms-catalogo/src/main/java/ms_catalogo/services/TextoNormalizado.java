package ms_catalogo.services;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza texto para búsquedas: sin tildes, en minúsculas y sin espacios repetidos.
 * Así "Córdoba", "cordoba" y "  CORDOBA " se consideran iguales.
 */
public final class TextoNormalizado {

    private TextoNormalizado() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** true si {@code texto} contiene {@code busqueda}, ignorando mayúsculas, tildes y espacios extra. */
    public static boolean contiene(String texto, String busqueda) {
        return normalizar(texto).contains(normalizar(busqueda));
    }
}
