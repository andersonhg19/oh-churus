package com.ohchurus.budget.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * ============================================================================
 * CUANTO SE PARECEN DOS DESCRIPCIONES
 * ============================================================================
 *
 * Sirve para dos cosas del importador, y las dos importan:
 *
 *   · Decidir si una fila del extracto ES un movimiento que ya tienes. El
 *     banco escribe "COMPRA EXITO CALLE 80" y tu escribiste "Mercado Exito";
 *     el importe y la fecha coinciden, y la descripcion no tiene por que.
 *   · Recordar en que categoria pusiste algo parecido la vez pasada.
 *
 * LEVENSHTEIN **Y** CONTENCION, y hace falta que sean las dos
 * ------------------------------------------------------------
 * "Contiene" a secas falla en los dos sentidos: "EXITO" contiene a "XI"
 * (falso positivo) y no reconoce "EXTIO" de un dedazo (falso negativo). La
 * distancia de edicion arregla eso.
 *
 * Pero la distancia sola tambien falla, y en el caso mas comun de todos: tu
 * escribes "Arriendo" y el banco escribe "ARRIENDO AGOSTO". Son catorce
 * caracteres contra ocho, la distancia da 0,5 y los declara distintos cuando
 * son obviamente lo mismo. Lo descubri escribiendo la prueba de extremo a
 * extremo: el pendiente del arriendo no casaba con su propia fila del
 * extracto.
 *
 * Asi que se usan las dos: uno contiene al otro (con un minimo de cuatro
 * caracteres, para que un "pago" suelto no se coma media lista) O la distancia
 * de edicion es pequena. Y ninguna de las dos decide sola: el cotejo exige
 * ademas importe identico y fecha dentro de cinco dias.
 *
 * SE NORMALIZA ANTES DE COMPARAR, y esto en Colombia no es opcional: los
 * extractos vienen en MAYUSCULAS y SIN TILDES, y lo que uno escribe a mano
 * lleva minusculas y tildes. Sin normalizar, "Cafetería" y "CAFETERIA" salen
 * como dos cosas distintas y el importador duplica.
 *
 * Codigo puro y sin librerias. La implementacion usa dos filas en vez de la
 * matriz completa porque no hace falta mas y asi no crece la memoria con
 * descripciones largas.
 */
public final class Parecido {

    private Parecido() {}

    /**
     * Deja el texto comparable: sin tildes, en minusculas, sin signos y con
     * los espacios colapsados.
     */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Distancia de edicion entre dos textos YA normalizados. */
    public static int distancia(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[] anterior = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) anterior[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int coste = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, anterior[j] + 1),
                        anterior[j - 1] + coste);
            }
            int[] intercambio = anterior;
            anterior = actual;
            actual = intercambio;
        }
        return anterior[b.length()];
    }

    /**
     * Cuanto se parecen, de 0 a 1, normalizando antes.
     *
     * Dos textos vacios se consideran IGUALES (1). Podria parecer discutible,
     * pero el caso real es un extracto sin columna de descripcion: si dos
     * vacios no se parecieran, el cotejo por importe y fecha nunca llegaria a
     * casar nada.
     */
    public static double cuanto(String a, String b) {
        String na = normalizar(a);
        String nb = normalizar(b);
        if (na.isEmpty() && nb.isEmpty()) return 1.0;
        int largo = Math.max(na.length(), nb.length());
        if (largo == 0) return 1.0;
        return 1.0 - ((double) distancia(na, nb) / largo);
    }

    /**
     * ¿Se parecen lo bastante como para ser lo mismo?
     *
     * El 0,6 sale de probar con descripciones de banco de verdad: "COMPRA
     * EXITO CALLE 80" contra "Mercado Exito" da 0,45 y no basta por si solo
     * —por eso el cotejo EXIGE ademas que el importe sea identico—, mientras
     * que "PAGO NEQUI" contra "Pago Nequi" da 1,0.
     *
     * Se prefiere quedarse corto: marcar como nuevo algo que ya estaba se ve
     * enseguida en la vista previa y se corrige; marcar como duplicado algo
     * que no lo era hace que un movimiento no entre nunca y nadie se entere.
     */
    public static boolean bastante(String a, String b) {
        String na = normalizar(a);
        String nb = normalizar(b);

        /*
         * QUE UNO CONTENGA AL OTRO CUENTA COMO PARECIDO, y esto no es un
         * apano: es EL caso comun del importador.
         *
         * Tu escribes "Arriendo" y el banco escribe "ARRIENDO AGOSTO". La
         * distancia de edicion los da como distintos —0,5 sobre catorce
         * caracteres— y sin embargo son obviamente lo mismo. Paso literalmente
         * al escribir la prueba de extremo a extremo: el pendiente del
         * arriendo no casaba con su propia fila del extracto.
         *
         * Es seguro porque el cotejo NO decide con esto solo: exige ademas
         * importe identico y fecha dentro de cinco dias. Y se pide un minimo
         * de cuatro caracteres para que un "pago" suelto no se coma media
         * lista.
         */
        String corto = na.length() <= nb.length() ? na : nb;
        String largo = na.length() <= nb.length() ? nb : na;
        if (corto.length() >= 4 && largo.contains(corto)) return true;

        return cuanto(a, b) >= 0.6;
    }
}
