package com.ohchurus.budget.util;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * EL LECTOR DE CSV
 * ============================================================================
 *
 * POR QUE ESCRITO A MANO Y NO UNA LIBRERIA
 * ----------------------------------------
 * Porque el proyecto no anade dependencias sin motivo, y aqui el motivo no
 * existe: un CSV bancario son cuatro reglas. Lo que si hay que hacer es
 * respetarlas TODAS, porque el partir por comas ingenuo funciona con el
 * fichero de ejemplo y falla con el primero de verdad:
 *
 *   · Un campo entre comillas puede llevar comas dentro. "Pago, cuota 3" es UN
 *     campo, y partir por comas lo convierte en dos y desplaza todo lo demas.
 *   · Dentro de un campo entrecomillado, "" es una comilla literal.
 *   · Un campo entrecomillado puede llevar SALTOS DE LINEA dentro, asi que no
 *     se puede leer linea a linea: hay que recorrer caracter a caracter.
 *   · Los bancos colombianos exportan con punto y coma tan a menudo como con
 *     coma, asi que el separador se detecta en vez de suponerse.
 *
 * Es codigo puro: entra texto, salen filas. Ni ficheros, ni codificaciones, ni
 * base de datos.
 */
public final class LectorCsv {

    private LectorCsv() {}

    private static final char[] CANDIDATOS = {',', ';', '\t', '|'};

    /**
     * Adivina el separador contando cual aparece mas veces FUERA de comillas
     * en la primera linea util.
     *
     * Contar a secas se equivoca con "Pago, cuota 3;15000": la coma de dentro
     * del texto ganaria al punto y coma que separa de verdad.
     */
    public static char separadorDe(String texto) {
        if (texto == null || texto.isEmpty()) return ',';

        char mejor = ',';
        int masVeces = -1;
        for (char candidato : CANDIDATOS) {
            int veces = 0;
            boolean entreComillas = false;
            for (int i = 0; i < texto.length(); i++) {
                char c = texto.charAt(i);
                if (c == '"') {
                    entreComillas = !entreComillas;
                } else if (c == '\n' && !entreComillas) {
                    break;
                } else if (c == candidato && !entreComillas) {
                    veces++;
                }
            }
            if (veces > masVeces) {
                masVeces = veces;
                mejor = candidato;
            }
        }
        return mejor;
    }

    /** Lee el texto entero como filas de campos. */
    public static List<List<String>> leer(String texto, char separador) {
        List<List<String>> filas = new ArrayList<>();
        if (texto == null || texto.isEmpty()) return filas;

        List<String> fila = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);

            if (entreComillas) {
                if (c == '"') {
                    /* Dos comillas seguidas dentro de un campo entrecomillado
                       son UNA comilla literal, no el final del campo. */
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    campo.append(c);
                }
                continue;
            }

            if (c == '"') {
                entreComillas = true;
            } else if (c == separador) {
                fila.add(campo.toString().trim());
                campo.setLength(0);
            } else if (c == '\n') {
                fila.add(campo.toString().trim());
                campo.setLength(0);
                if (!esFilaVacia(fila)) filas.add(fila);
                fila = new ArrayList<>();
            } else if (c != '\r') {
                campo.append(c);
            }
        }

        /* La ultima fila no suele acabar en salto de linea, y perderla
           silenciosamente significa perder el ultimo movimiento del extracto. */
        fila.add(campo.toString().trim());
        if (!esFilaVacia(fila)) filas.add(fila);

        return filas;
    }

    public static List<List<String>> leer(String texto) {
        return leer(texto, separadorDe(texto));
    }

    /**
     * Las lineas en blanco del final del fichero no son movimientos vacios.
     * Sin esto, un extracto que acaba con un salto de linea de mas produce una
     * fila de campos vacios que el importador intenta interpretar.
     */
    private static boolean esFilaVacia(List<String> fila) {
        return fila.stream().allMatch(c -> c == null || c.isEmpty());
    }
}
