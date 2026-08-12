package com.ohchurus.budget.client;

/**
 * Traduce un correo al id de usuario que usa budget-service.
 *
 * Nace de un bug de usabilidad: para invitar a alguien al nucleo familiar
 * habia que teclear el id de fila de la base de datos ("Ej: 4"). Nadie conoce
 * su propio id, asi que la funcion estrella de la app era inusable.
 *
 * Es una interfaz y no una clase suelta porque los usuarios viven en OTRA base
 * de datos (auth_db) y en otro servicio: budget-service no puede consultarlos
 * con un repositorio. Separar el contrato de la llamada HTTP deja que las
 * pruebas ejerciten la logica del hogar sin levantar auth-service.
 */
public interface DirectorioDeUsuarios {

    /**
     * Id del usuario con ese correo, o null si no existe o el directorio no
     * responde. Null es "no lo puedo confirmar", y quien llama debe tratarlo
     * como "no existe": ante la duda no se mete a nadie en una casa ajena.
     */
    Long idPorCorreo(String correo);
}
