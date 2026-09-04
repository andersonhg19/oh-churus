package com.ohchurus.auth.service.impl;

import com.ohchurus.auth.dto.input.AppConfigSaveDTO;
import com.ohchurus.auth.dto.output.ResultAppConfigDTO;
import com.ohchurus.auth.dto.output.ResultDTO;
import com.ohchurus.auth.dto.output.ResultPreferenciasDTO;
import com.ohchurus.auth.entity.AppConfiguration;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.enums.Message;
import com.ohchurus.auth.repository.AppConfigurationRepository;
import com.ohchurus.auth.repository.UserRepository;
import com.ohchurus.auth.service.PreferenciasService;
import com.ohchurus.auth.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Como se le muestran los importes a cada quien.
 *
 * DOS NIVELES: lo que vale para toda la instalacion y lo que esa persona
 * prefiere. La regla es una sola frase: si el usuario no ha elegido, manda la
 * configuracion global.
 *
 * Eso explica por que las columnas del usuario admiten nulo en vez de llevar
 * un valor por defecto. Con un DEFAULT 'COP' en la columna, cada cuenta se
 * quedaria congelada con la moneda que hubiera el dia en que se creo, y
 * cambiar la global no serviria de nada para nadie.
 */
@Slf4j
@Service
public class PreferenciasServiceImpl implements PreferenciasService {

    private static final String CLAVE_MONEDA = "app.currency";
    private static final String CLAVE_DECIMALES = "app.decimalPlaces";
    private static final String CLAVE_LOCALE = "app.locale";

    /* Ultimo recurso si la tabla de configuracion estuviera vacia. No es la
       fuente de verdad: es lo que impide que la app se quede sin formato. */
    private static final String MONEDA_DE_EMERGENCIA = "COP";
    private static final int DECIMALES_DE_EMERGENCIA = 0;
    private static final String LOCALE_DE_EMERGENCIA = "es-CO";

    private final AppConfigurationRepository configuracion;
    private final UserRepository usuarios;

    public PreferenciasServiceImpl(AppConfigurationRepository configuracion, UserRepository usuarios) {
        this.configuracion = configuracion;
        this.usuarios = usuarios;
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO misPreferencias() {
        Long yo = SecurityUtils.getAuthenticatedUserId();
        Optional<User> usuario = yo == null ? Optional.empty() : usuarios.findByIdAndActiveTrue(yo);

        String moneda = usuario.map(User::getCurrency).filter(v -> v != null && !v.isBlank())
                .orElseGet(() -> valorDe(CLAVE_MONEDA, MONEDA_DE_EMERGENCIA));
        int decimales = usuario.map(User::getDecimalPlaces)
                .orElseGet(() -> entero(CLAVE_DECIMALES, DECIMALES_DE_EMERGENCIA));
        String locale = valorDe(CLAVE_LOCALE, LOCALE_DE_EMERGENCIA);

        return new ResultDTO(new ResultPreferenciasDTO(moneda, decimales, locale,
                usuario.map(User::getCurrency).isPresent()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getConfiguracion() {
        List<ResultAppConfigDTO> lista = configuracion.findAllByOrderByConfigKeyAsc().stream()
                .map(c -> new ResultAppConfigDTO(c.getId(), c.getConfigKey(), c.getValue(),
                        c.getDescription(), c.getActive()))
                .toList();
        return new ResultDTO(lista);
    }

    @Override
    @Transactional
    public ResultDTO guardarConfiguracion(AppConfigSaveDTO dto) {
        String clave = dto.getConfigKey().trim();

        /* Los decimales se comprueban aqui ademas de en la base: un 12 en esa
           clave rompe el formateador del movil, y ese fallo llega como una
           pantalla en blanco sin explicacion. */
        if (CLAVE_DECIMALES.equals(clave) && !esUnNumeroDeDecimales(dto.getValue())) {
            return new ResultDTO(false, Message.Msj.invalidConfigValue.toString(), 112);
        }

        AppConfiguration ajuste = configuracion.findByConfigKey(clave)
                .orElseGet(() -> AppConfiguration.builder().configKey(clave).active(true).build());
        ajuste.setValue(dto.getValue().trim());
        if (dto.getDescription() != null) {
            ajuste.setDescription(dto.getDescription());
        }
        configuracion.save(ajuste);

        log.info("Configuracion {} = {}", clave, ajuste.getValue());
        return new ResultDTO(new ResultAppConfigDTO(ajuste.getId(), ajuste.getConfigKey(),
                ajuste.getValue(), ajuste.getDescription(), ajuste.getActive()));
    }

    private String valorDe(String clave, String siNoEsta) {
        return configuracion.findByConfigKeyAndActiveTrue(clave)
                .map(AppConfiguration::getValue)
                .filter(v -> !v.isBlank())
                .orElse(siNoEsta);
    }

    private int entero(String clave, int siNoEsta) {
        String valor = valorDe(clave, String.valueOf(siNoEsta));
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            /* Un valor escrito a mano en la base no puede tumbar la pantalla de
               nadie: se avisa y se sigue con lo de siempre. */
            log.warn("La configuracion {} no es un numero ({}); se usa {}", clave, valor, siNoEsta);
            return siNoEsta;
        }
    }

    private static boolean esUnNumeroDeDecimales(String valor) {
        try {
            int n = Integer.parseInt(valor.trim());
            return n >= 0 && n <= 4;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
