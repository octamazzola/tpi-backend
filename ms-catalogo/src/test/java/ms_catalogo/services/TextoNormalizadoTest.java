package ms_catalogo.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoNormalizadoTest {

    @Test
    void ignoraMayusculasTildesYEspaciosExtra() {
        assertThat(TextoNormalizado.normalizar("  Córdoba   Capital ")).isEqualTo("cordoba capital");
        assertThat(TextoNormalizado.contiene("Córdoba", "CORDOBA")).isTrue();
        assertThat(TextoNormalizado.contiene("San José de la Dormida", "jose")).isTrue();
    }

    @Test
    void coincidenciaParcial() {
        assertThat(TextoNormalizado.contiene("Villa Carlos Paz", "carlos paz")).isTrue();
        assertThat(TextoNormalizado.contiene("Villa Carlos Paz", "villa")).isTrue();
        assertThat(TextoNormalizado.contiene("Villa Carlos Paz", "Mendoza")).isFalse();
    }

    @Test
    void ciudadNulaNoCoincide() {
        assertThat(TextoNormalizado.contiene(null, "tanti")).isFalse();
    }
}
