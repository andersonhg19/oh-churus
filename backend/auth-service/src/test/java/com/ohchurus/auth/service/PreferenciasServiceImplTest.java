package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.AppConfigSaveDTO;
import com.ohchurus.auth.dto.output.ResultAppConfigDTO;
import com.ohchurus.auth.dto.output.ResultDTO;
import com.ohchurus.auth.dto.output.ResultPreferenciasDTO;
import com.ohchurus.auth.entity.AppConfiguration;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.repository.AppConfigurationRepository;
import com.ohchurus.auth.repository.UserRepository;
import com.ohchurus.auth.service.impl.PreferenciasServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * La regla es una sola frase: si el usuario no ha elegido, manda la
 * configuracion global. Todo lo de aqui comprueba esa frase y lo que pasa
 * cuando algo de lo que depende no esta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Preferencias de formato")
class PreferenciasServiceImplTest {

    @Mock private AppConfigurationRepository configuracion;
    @Mock private UserRepository usuarios;

    @InjectMocks private PreferenciasServiceImpl servicio;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void entraComo(Long userId) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("quien@sea", null, List.of());
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * Se declara con lenient porque cada prueba fija solo el ajuste que le
     * interesa, y el servicio consulta los tres —moneda, decimales y formato—
     * en cada llamada. Sin esto, Mockito trata como error el stub que esa
     * prueba concreta no llega a usar.
     */
    private void ajuste(String clave, String valor) {
        lenient().when(configuracion.findByConfigKeyAndActiveTrue(clave)).thenReturn(
                Optional.of(AppConfiguration.builder().configKey(clave).value(valor).active(true).build()));
    }

    private static User usuarioCon(String moneda, Integer decimales) {
        return User.builder().id(1L).name("A").email("a@x.com").password("x")
                .budgetStartDay(1).active(true)
                .currency(moneda).decimalPlaces(decimales).build();
    }

    private static ResultPreferenciasDTO datos(ResultDTO r) {
        return (ResultPreferenciasDTO) r.getObject();
    }

    @Nested
    @DisplayName("Que manda")
    class QueManda {

        @Test
        @DisplayName("si la persona eligio, manda lo suyo")
        void mandaLoDelUsuario() {
            entraComo(1L);
            when(usuarios.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(usuarioCon("EUR", 2)));
            ajuste("app.locale", "es-ES");

            ResultPreferenciasDTO p = datos(servicio.misPreferencias());

            assertThat(p.getCurrency()).isEqualTo("EUR");
            assertThat(p.getDecimalPlaces()).isEqualTo(2);
            assertThat(p.getPropia()).isTrue();
        }

        @Test
        @DisplayName("si no eligio, manda lo global")
        void mandaLoGlobal() {
            entraComo(1L);
            when(usuarios.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(usuarioCon(null, null)));
            ajuste("app.currency", "USD");
            ajuste("app.decimalPlaces", "2");
            ajuste("app.locale", "en-US");

            ResultPreferenciasDTO p = datos(servicio.misPreferencias());

            assertThat(p.getCurrency()).isEqualTo("USD");
            assertThat(p.getDecimalPlaces()).isEqualTo(2);
            assertThat(p.getPropia()).isFalse();
        }

        @Test
        @DisplayName("una moneda propia en blanco cuenta como no elegida")
        void monedaEnBlanco() {
            entraComo(1L);
            when(usuarios.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(usuarioCon("   ", null)));
            ajuste("app.currency", "COP");

            assertThat(datos(servicio.misPreferencias()).getCurrency()).isEqualTo("COP");
        }

        @Test
        @DisplayName("sin configuracion global tampoco se queda sin formato")
        void sinConfiguracion() {
            /* La tabla vacia no puede dejar la app sin poder escribir un
               importe: hay un ultimo recurso, que no es la fuente de verdad
               pero impide la pantalla en blanco. */
            entraComo(1L);
            when(usuarios.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(usuarioCon(null, null)));
            when(configuracion.findByConfigKeyAndActiveTrue(anyString())).thenReturn(Optional.empty());

            ResultPreferenciasDTO p = datos(servicio.misPreferencias());

            assertThat(p.getCurrency()).isEqualTo("COP");
            assertThat(p.getDecimalPlaces()).isZero();
            assertThat(p.getLocale()).isEqualTo("es-CO");
        }

        @Test
        @DisplayName("UN VALOR MAL ESCRITO EN LA BASE NO TUMBA LA PANTALLA")
        void valorNoNumerico() {
            /* Alguien escribe "dos" en app.decimalPlaces. Sin esto, cada
               peticion de preferencias respondería con un error y la app se
               quedaria sin poder pintar ni una cifra. */
            entraComo(1L);
            when(usuarios.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(usuarioCon(null, null)));
            ajuste("app.decimalPlaces", "dos");

            assertThat(datos(servicio.misPreferencias()).getDecimalPlaces()).isZero();
        }

        @Test
        @DisplayName("sin sesion se responde lo global, no un error")
        void sinSesion() {
            SecurityContextHolder.clearContext();
            ajuste("app.currency", "COP");

            ResultDTO resultado = servicio.misPreferencias();

            assertThat(resultado.isCorrect()).isTrue();
            assertThat(datos(resultado).getPropia()).isFalse();
            verify(usuarios, never()).findByIdAndActiveTrue(any());
        }
    }

    @Nested
    @DisplayName("Configuracion de la instalacion")
    class Configuracion {

        @SuppressWarnings("unchecked")
        private List<ResultAppConfigDTO> lista(ResultDTO r) {
            return (List<ResultAppConfigDTO>) r.getObject();
        }

        @Test
        @DisplayName("lista los ajustes")
        void listaAjustes() {
            when(configuracion.findAllByOrderByConfigKeyAsc()).thenReturn(List.of(
                    AppConfiguration.builder().id(1L).configKey("app.currency").value("COP").active(true).build()));

            assertThat(lista(servicio.getConfiguracion())).hasSize(1);
        }

        @Test
        @DisplayName("cambia uno que ya existe")
        void cambiaExistente() {
            AppConfiguration existente = AppConfiguration.builder()
                    .id(1L).configKey("app.currency").value("COP").active(true).build();
            when(configuracion.findByConfigKey("app.currency")).thenReturn(Optional.of(existente));
            when(configuracion.save(any(AppConfiguration.class))).thenAnswer(i -> i.getArgument(0));

            ResultDTO resultado = servicio.guardarConfiguracion(
                    new AppConfigSaveDTO("app.currency", "EUR", null));

            assertThat(resultado.isCorrect()).isTrue();
            assertThat(existente.getValue()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("crea el que no existia")
        void creaNuevo() {
            when(configuracion.findByConfigKey("app.nuevo")).thenReturn(Optional.empty());
            when(configuracion.save(any(AppConfiguration.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(servicio.guardarConfiguracion(
                    new AppConfigSaveDTO("app.nuevo", "valor", "para algo")).isCorrect()).isTrue();
        }

        @Test
        @DisplayName("UN NUMERO DE DECIMALES IMPOSIBLE NO SE GUARDA")
        void decimalesImposibles() {
            /* Un 12 aqui rompe el formateador del movil, y ese fallo llega
               como una pantalla en blanco sin explicacion. */
            ResultDTO resultado = servicio.guardarConfiguracion(
                    new AppConfigSaveDTO("app.decimalPlaces", "12", null));

            assertThat(resultado.isCorrect()).isFalse();
            assertThat(resultado.getErrorCode()).isEqualTo(112);
            verify(configuracion, never()).save(any());
        }

        @Test
        @DisplayName("ni un texto donde va un numero")
        void decimalesNoNumericos() {
            assertThat(servicio.guardarConfiguracion(
                    new AppConfigSaveDTO("app.decimalPlaces", "dos", null)).isCorrect()).isFalse();
            verify(configuracion, never()).save(any());
        }

        @Test
        @DisplayName("pero cero decimales si es valido")
        void ceroDecimales() {
            when(configuracion.findByConfigKey("app.decimalPlaces")).thenReturn(Optional.empty());
            when(configuracion.save(any(AppConfiguration.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(servicio.guardarConfiguracion(
                    new AppConfigSaveDTO("app.decimalPlaces", "0", null)).isCorrect()).isTrue();
        }
    }
}
