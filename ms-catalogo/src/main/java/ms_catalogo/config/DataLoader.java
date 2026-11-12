package ms_catalogo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ms_catalogo.entities.Actividad;
import ms_catalogo.entities.Categoria;
import ms_catalogo.entities.Dificultad;
import ms_catalogo.repositories.ActividadRepository;
import ms_catalogo.repositories.CategoriaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Componente que se ejecuta al iniciar la aplicación (CommandLineRunner).
 * Carga datos semilla reales de la Provincia de Córdoba para que la base de datos
 * H2 en memoria cuente con información lista para probar desde Swagger UI o Postman.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements CommandLineRunner {

    private final CategoriaRepository categoriaRepository;
    private final ActividadRepository actividadRepository;

    @Override
    public void run(String... args) {
        if (categoriaRepository.count() == 0) {
            log.info("Iniciando la carga de datos semilla para ms-catalogo...");

            // 1. Categorías representativas de turismo y aventura
            Categoria trekking = Categoria.builder()
                    .nombre("Trekking y Senderismo")
                    .descripcion("Ascensos, caminatas por cerros, quebradas y senderos naturales de las sierras.")
                    .build();

            Categoria nautica = Categoria.builder()
                    .nombre("Navegación y Deportes Acuáticos")
                    .descripcion("Paseos náuticos, kayak, navegación a vela y actividades en diques y lagos.")
                    .build();

            Categoria cabalgata = Categoria.builder()
                    .nombre("Cabalgatas y Turismo Rural")
                    .descripcion("Recorridos a caballo guiados por valles, estancias jesuíticas y pinares.")
                    .build();

            Categoria aventura = Categoria.builder()
                    .nombre("Aventura y Escalada")
                    .descripcion("Escalada en roca, tirolesa, rappel y expediciones de alta adrenalina.")
                    .build();

            categoriaRepository.saveAll(List.of(trekking, nautica, cabalgata, aventura));
            log.info("Categorías precargadas con éxito (4 registros).");

            // 2. Actividades y excursiones reales en Córdoba con coordenadas GPS precisas
            Actividad champaqui = Actividad.builder()
                    .titulo("Ascenso al Cerro Champaquí")
                    .descripcion("Expedición de trekking al cerro más alto de Córdoba (2790 msnm), partiendo desde Villa Alpina por la Quebrada del Jesús.")
                    .categoria(trekking)
                    .dificultad(Dificultad.ALTA)
                    .precioBase(45000.0)
                    .cupoMaximo(15)
                    .latitudInicio(-31.9868)
                    .longitudInicio(-64.9351)
                    .direccionPuntoEncuentro("Villa Alpina, Valle de Calamuchita, Córdoba")
                    .ciudad("Villa Alpina")
                    .requiereBuenClima(true)
                    .edadMinima(14)
                    .edadMaxima(65)
                    .activa(true)
                    .build();

            Actividad sanRoque = Actividad.builder()
                    .titulo("Paseo Náutico en Dique San Roque")
                    .descripcion("Travesía guiada en catamarán con avistamiento de aves y recorrido por las bahías del embalse.")
                    .categoria(nautica)
                    .dificultad(Dificultad.BAJA)
                    .precioBase(18000.0)
                    .cupoMaximo(35)
                    .latitudInicio(-31.3970)
                    .longitudInicio(-64.4630)
                    .direccionPuntoEncuentro("Costanera Centro, Villa Carlos Paz, Córdoba")
                    .ciudad("Villa Carlos Paz")
                    .requiereBuenClima(true)
                    // Temporada de navegación: septiembre a abril
                    .mesInicioTemporada(9)
                    .mesFinTemporada(4)
                    .activa(true)
                    .build();

            Actividad cumbrecita = Actividad.builder()
                    .titulo("Cabalgata al Peñón del Águila y Bosques Suizos")
                    .descripcion("Recorrido a caballo entre pinares y arroyos cristalinos por el pintoresco pueblo peatonal de La Cumbrecita.")
                    .categoria(cabalgata)
                    .dificultad(Dificultad.MEDIA)
                    .precioBase(32000.0)
                    .cupoMaximo(10)
                    .latitudInicio(-31.9015)
                    .longitudInicio(-64.7731)
                    .direccionPuntoEncuentro("Puente de Acceso Peatonal, La Cumbrecita, Córdoba")
                    .ciudad("La Cumbrecita")
                    .requiereBuenClima(false)
                    .activa(true)
                    .build();

            Actividad gigantes = Actividad.builder()
                    .titulo("Escalada y Rappel en Los Gigantes")
                    .descripcion("Práctica de escalada en granito y descenso en rappel de 40 metros en el Macizo de Los Gigantes.")
                    .categoria(aventura)
                    .dificultad(Dificultad.ALTA)
                    .precioBase(38000.0)
                    .cupoMaximo(12)
                    .latitudInicio(-31.4172)
                    .longitudInicio(-64.8347)
                    .direccionPuntoEncuentro("Refugio La Rotonda, Pampa de Achala, Córdoba")
                    .ciudad("Tanti")
                    .requiereBuenClima(true)
                    .edadMinima(18)
                    // Temporada de escalada: marzo a noviembre (evita el calor y las tormentas de verano)
                    .mesInicioTemporada(3)
                    .mesFinTemporada(11)
                    .activa(true)
                    .build();

            actividadRepository.saveAll(List.of(champaqui, sanRoque, cumbrecita, gigantes));
            log.info("Excursiones precargadas con éxito (4 registros).");
            log.info("--> Documentación OpenAPI disponible en: http://localhost:8081/swagger-ui.html");
        }
        completarCiudadesFaltantes();
    }

    /**
     * Migración de datos: las actividades creadas antes de existir el campo "ciudad" lo tienen vacío.
     * Se completa con la localidad de la dirección del punto de encuentro, que por convención es el
     * anteúltimo tramo: "Costanera Centro, Villa Carlos Paz, Córdoba" -> "Villa Carlos Paz".
     */
    private void completarCiudadesFaltantes() {
        List<Actividad> sinCiudad = actividadRepository.findAll().stream()
                .filter(a -> a.getCiudad() == null || a.getCiudad().isBlank())
                .toList();
        for (Actividad a : sinCiudad) {
            String direccion = a.getDireccionPuntoEncuentro() == null ? "" : a.getDireccionPuntoEncuentro();
            String[] partes = direccion.split(",");
            String ciudad = (partes.length >= 2 ? partes[partes.length - 2] : partes[0]).trim();
            a.setCiudad(ciudad.isBlank() ? "Sin especificar" : ciudad);
        }
        if (!sinCiudad.isEmpty()) {
            actividadRepository.saveAll(sinCiudad);
            log.info("Se completó la ciudad de {} actividades existentes.", sinCiudad.size());
        }
    }
}
