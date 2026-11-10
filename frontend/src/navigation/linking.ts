import { Platform } from 'react-native';
import { getPathFromState as getPathFromStateDefault, LinkingOptions } from '@react-navigation/native';

/*
 * RUTAS LEGIBLES EN LA WEB.
 *
 * Antes la URL era siempre "/": el navegador no tenia historial, asi que el
 * boton atras (o el gesto atras del telefono en la web) sacaba de la app en
 * vez de volver a la pantalla anterior, y recargar devolvia siempre al inicio.
 *
 * Ahora cada pantalla tiene su ruta (/presupuesto, /deudas, ...) y cada
 * navegacion deja su entrada en el historial.
 *
 * Los parametros que son objetos (el movimiento que se edita, la categoria del
 * formulario) NO se escriben en la URL: escritos ahi se volverian el texto
 * "[object Object]" al recargar y la pantalla recibiria basura. Se quitan, y al
 * recargar esa pantalla abre su forma por defecto (un formulario nuevo).
 */

const numeroOTexto = (v: string) => (/^\d+$/.test(v) ? Number(v) : v);
const numeroOVacio = (v: string) => (/^\d+$/.test(v) ? Number(v) : undefined);

/** La configuracion de rutas. Los nombres son los de AppNavigator. */
export const RUTAS: LinkingOptions<any>['config'] = {
  screens: {
    Login: 'entrar',
    Register: 'registro',
    MainTabs: {
      screens: {
        FinanceModule: {
          /* Con esto, entrar directo a /presupuesto deja el inicio debajo: la
             flecha atras de la pantalla lleva a algun lado util. */
          initialRouteName: 'Inicio',
          screens: {
            Inicio: '',
            Movimientos: { path: 'movimientos', parse: { categoriaId: numeroOVacio } },
            Presupuesto: 'presupuesto',
            Categoria: { path: 'presupuesto/categoria/:categoryId', parse: { categoryId: numeroOVacio } },
            Cierre: 'cierre',
            PorConfirmar: { path: 'por-confirmar', parse: { quien: numeroOTexto } },
            Ingresos: 'ingresos',
            Ahorros: 'ahorros',
            Deudas: 'deudas',
            Plan: 'plan',
            ScheduledList: 'programados',
            ScheduledForm: 'programados/formulario',
            CategoryDrillDown: 'movimientos/categoria/:categoryId',
            SettingsMain: 'ajustes',
            ExportImport: 'ajustes/exportar',
            PeriodConfig: 'ajustes/dia-de-corte',
            CurrencyConfig: 'ajustes/moneda',
            Household: 'ajustes/hogar',
            CategoriesList: 'ajustes/categorias',
            CategoryForm: 'ajustes/categorias/formulario',
            AdminUsers: 'ajustes/admin/usuarios',
            AdminRoles: 'ajustes/admin/roles',
            AdminModules: 'ajustes/admin/modulos',
            AdminAudit: 'ajustes/admin/auditoria',
          },
        },
      },
    },
    Anotar: { path: 'anotar', parse: { categoriaId: numeroOVacio } },
    MovementFormModal: 'movimiento',
  },
};

const esPrimitivo = (v: unknown) => v === null || ['string', 'number', 'boolean'].includes(typeof v);

/** Quita de un estado de navegacion (y de sus hijos) los parametros que no caben en una URL. */
export const sinParametrosObjeto = (state: any): any => {
  if (!state || !Array.isArray(state.routes)) return state;
  return {
    ...state,
    routes: state.routes.map((r: any) => {
      const params = r.params
        ? Object.fromEntries(Object.entries(r.params).filter(([, v]) => v !== undefined && esPrimitivo(v)))
        : undefined;
      return {
        ...r,
        params: params && Object.keys(params).length > 0 ? params : undefined,
        state: r.state ? sinParametrosObjeto(r.state) : r.state,
      };
    }),
  };
};

export const linking: LinkingOptions<any> = {
  /* Solo en la web: en el telefono no hay barra de direcciones, y el boton
     atras de Android ya lo maneja la pila. */
  enabled: Platform.OS === 'web',
  prefixes: [],
  config: RUTAS,
  getPathFromState: (state, options) => getPathFromStateDefault(sinParametrosObjeto(state), options),
};

/**
 * Ir al inicio desde cualquier pantalla, este en la pila de finanzas, en un
 * modal de la raiz o sin nada debajo (una URL abierta directamente).
 */
export const irAlInicio = (navigation: any) => {
  if (!navigation) return;
  try {
    const nombres: string[] = navigation.getState?.()?.routeNames || [];
    if (nombres.includes('Inicio')) navigation.navigate('Inicio');
    else if (nombres.includes('MainTabs')) navigation.navigate('MainTabs', { screen: 'FinanceModule', params: { screen: 'Inicio' } });
    else if (navigation.canGoBack?.()) navigation.goBack();
  } catch (e) {
    console.error('[irAlInicio] no se pudo volver al inicio', e);
  }
};

/**
 * Volver: a la pantalla anterior si la hay; si no (se entro directo por la
 * URL o se recargo), al inicio. Nunca a ninguna parte.
 */
export const volverOInicio = (navigation: any) => {
  if (navigation?.canGoBack?.()) navigation.goBack();
  else irAlInicio(navigation);
};
