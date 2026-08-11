package com.ohchurus.budget.util;

import com.ohchurus.budget.entity.Category;
import com.ohchurus.budget.repository.CategoryRepository;
import com.ohchurus.budget.service.impl.HouseholdServiceImpl;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * ¿Puede quien hace esta peticion tocar este dato?
 *
 * Un solo sitio donde se responde esa pregunta, en vez de repetir la condicion
 * en cada metodo de cada servicio. Antes no se respondia en ninguno: el userId
 * llegaba en el cuerpo y los metodos que reciben un id (get/{id}, delete/{id},
 * confirm/{id}) no miraban de quien era el dato.
 *
 * LA REGLA
 * --------
 * Un dato es tuyo si lo creaste tu, o si vive en una categoria COMPARTIDA de
 * un hogar al que perteneces. Eso ultimo es la funcionalidad estrella de la
 * app —el gasto del arriendo lo ven los dos— y por eso no basta con comparar
 * userId: hay que mirar tambien la categoria.
 *
 * Ante la duda, NO. Un dato sin dueno no es de todos.
 */
@Component
public class ControlAcceso {

    private final CategoryRepository categorias;
    private final HouseholdServiceImpl hogares;

    public ControlAcceso(CategoryRepository categorias, HouseholdServiceImpl hogares) {
        this.categorias = categorias;
        this.hogares = hogares;
    }

    /** Id del usuario autenticado. null si la peticion no trae token valido. */
    public Long usuarioActual() {
        return SecurityUtils.getAuthenticatedUserId();
    }

    /** ¿Es mio por creacion? */
    public boolean esMio(Long duenoDelRecurso) {
        Long yo = usuarioActual();
        return yo != null && yo.equals(duenoDelRecurso);
    }

    /** ¿Pertenezco al hogar dueno de esta categoria compartida? */
    public boolean esDeMiHogar(Long householdId) {
        Long yo = usuarioActual();
        if (yo == null || householdId == null) return false;
        List<Long> mios = hogares.getHouseholdIds(yo);
        return mios != null && mios.contains(householdId);
    }

    /** Acceso a una categoria: mia, o compartida en un hogar mio. */
    public boolean puedeVerCategoria(Category categoria) {
        if (categoria == null) return false;
        return esMio(categoria.getUserId()) || esDeMiHogar(categoria.getHouseholdId());
    }

    /**
     * Acceso a un dato colgado de una categoria (movimiento, programado,
     * asignacion). Es mio si lo cree yo, o si esta en una categoria compartida
     * de mi hogar: asi la pareja sigue viendo el gasto comun sin que un extrano
     * pueda leer nada.
     */
    public boolean puedeVer(Long duenoDelRecurso, Long categoriaId) {
        if (esMio(duenoDelRecurso)) return true;
        if (categoriaId == null) return false;
        Optional<Category> cat = categorias.findByIdAndActiveTrue(categoriaId);
        return cat.isPresent() && esDeMiHogar(cat.get().getHouseholdId());
    }
}
