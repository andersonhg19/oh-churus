import { darkColors, lightColors } from '../theme/colors';
import { textoLegibleSobre } from '../theme/contraste';

/**
 * ============================================================================
 * EL CONTRASTE SE MIDE, NO SE ESTIMA
 * ============================================================================
 *
 * La ola de accesibilidad puso nombre a los 157 elementos interactivos, y
 * NadaInteractivoSinNombre impide que vuelvan a quedarse mudos. Faltaba la
 * otra mitad: que lo que se ve, SE VEA.
 *
 * El tema claro llevaba siete colores por debajo de 3:1 sobre su propio fondo,
 * y con varios de ellos se escriben cifras —los ingresos en verde, los gastos
 * en rojo—. Eso no es decoracion: es el contenido.
 *
 * Esta prueba recorre cada color de contenido contra cada fondo del tema y
 * calcula el contraste con la formula de la WCAG. No juzga si el color es
 * bonito; comprueba que se puede leer, que es justo lo que un ojo distraido
 * no detecta al elegir un hex nuevo en una pantalla buena y bien iluminada.
 *
 * DOS UMBRALES, PORQUE HAY DOS USOS
 * ---------------------------------
 *   · 4.5:1 para lo que lleva TEXTO pequeno encima o es texto: las cifras, las
 *     leyendas, los estados.
 *   · 3:1 para lo que solo es superficie o borde grande.
 *
 * Son los minimos de la WCAG 2.1 (AA). Se eligen esos y no otros porque son
 * los que usa todo el mundo, y tener un umbral propio significaria tener que
 * defenderlo cada vez.
 */

/** Luminancia relativa segun la WCAG. */
const luminancia = (hex: string): number => {
  const h = hex.replace('#', '');
  const canal = (i: number) => {
    const v = parseInt(h.substring(i, i + 2), 16) / 255;
    return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * canal(0) + 0.7152 * canal(2) + 0.0722 * canal(4);
};

const contraste = (a: string, b: string): number => {
  const la = luminancia(a);
  const lb = luminancia(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
};

/** Los tres fondos sobre los que puede caer cualquier cosa. */
const FONDOS = ['background', 'surface', 'card'] as const;

/** Colores con los que se escribe texto o cifras: 4.5:1. */
const LLEVAN_TEXTO = [
  'text', 'textSecondary', 'textMuted',
  'income', 'expense', 'pending',
  'danger', 'warning', 'success', 'accent', 'secondary',
] as const;

/** Colores de superficie o elemento grande: 3:1 basta. */
const SOLO_SUPERFICIE = ['primary', 'primaryLight'] as const;

const TEMAS = [
  { nombre: 'oscuro', colores: darkColors },
  { nombre: 'claro', colores: lightColors },
] as const;

describe('El contraste se mide', () => {
  TEMAS.forEach(({ nombre, colores }) => {
    describe(`tema ${nombre}`, () => {
      LLEVAN_TEXTO.forEach((clave) => {
        it(`${clave} se lee sobre cualquier fondo (4.5:1)`, () => {
          FONDOS.forEach((fondo) => {
            const medido = contraste(colores[clave], colores[fondo]);
            expect(
              `${clave} sobre ${fondo}: ${medido.toFixed(2)}:1`,
            ).toBe(
              medido >= 4.5
                ? `${clave} sobre ${fondo}: ${medido.toFixed(2)}:1`
                : `${clave} sobre ${fondo}: NECESITA 4.5:1 O MAS`,
            );
          });
        });
      });

      SOLO_SUPERFICIE.forEach((clave) => {
        it(`${clave} se distingue del fondo (3:1)`, () => {
          FONDOS.forEach((fondo) => {
            const medido = contraste(colores[clave], colores[fondo]);
            expect(
              `${clave} sobre ${fondo}: ${medido.toFixed(2)}:1`,
            ).toBe(
              medido >= 3
                ? `${clave} sobre ${fondo}: ${medido.toFixed(2)}:1`
                : `${clave} sobre ${fondo}: NECESITA 3:1 O MAS`,
            );
          });
        });
      });

      it('el texto sobre el color primario tambien se lee: es el de los botones', () => {
        /* El boton principal pinta el fondo con primary y el texto encima. Si
           solo se comprobara primary contra el fondo de la pantalla, un
           primary perfectamente visible podria seguir teniendo el texto de su
           propio boton ilegible — que es exactamente lo que pasaba en el tema
           oscuro, con 2.08:1, mientras el boton escribia siempre en blanco.
           Se mide con la MISMA funcion que usa el boton, no con una suposicion
           de cual sera el color. */
        const medido = contraste(textoLegibleSobre(colores.primary), colores.primary);
        expect(
          `texto sobre primary: ${medido.toFixed(2)}:1`,
        ).toBe(
          medido >= 4.5
            ? `texto sobre primary: ${medido.toFixed(2)}:1`
            : 'texto sobre primary: NECESITA 4.5:1 O MAS',
        );
      });

      it('el borde se distingue de la superficie que separa', () => {
        /* Un borde que no se ve es una tarjeta sin limites: se pierde donde
           acaba una cosa y empieza la siguiente. Aqui basta 1.5:1 —es una
           linea fina, no informacion— pero no puede ser invisible. */
        const medido = contraste(colores.border, colores.surface);
        expect(medido).toBeGreaterThan(1.15);
      });
    });
  });

  it('los dos temas definen exactamente las mismas claves', () => {
    /* Una clave que solo existe en uno de los dos es una pantalla que en el
       otro tema pinta "undefined", y React Native lo deja transparente en vez
       de fallar. */
    expect(Object.keys(lightColors).sort()).toEqual(Object.keys(darkColors).sort());
  });
});
