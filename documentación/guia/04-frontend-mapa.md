# 04 · Mapa del frontend (Expo / React Native, usado como web en el celular)

> Guía para otro Claude (o una persona) que tenga que tocar el frontend de **Oh Churus**.
> Está escrita leyendo el código de `frontend/` (noviembre 2026, después de la "ola 7.5"). Los documentos
> viejos de `documentación/` están desactualizados: **si este archivo y el código no coinciden, manda el código**.
> El `CLAUDE.md` y el `AGENTS.md` de la raíz del repo son de **otro proyecto**: no aplican aquí.
>
> La app está **en producción** y la usa una pareja (en el código aparecen como "Andy" y "Samy"). Se usa casi
> siempre como **web en el navegador del celular**, así que lo que pase en `Platform.OS === 'web'` importa más que lo nativo.

---

## 1. Stack, arranque y construcción

| Pieza | Versión (package.json) |
|---|---|
| Expo | `~55.0.7` |
| React / React DOM | `19.2.0` |
| React Native | `0.83.2` (+ `react-native-web ^0.21.0`) |
| React Navigation | `native ^7.1`, `native-stack ^7.14`, `drawer ^7.9`, `bottom-tabs ^7.15` (bottom-tabs ya **no se usa**) |
| HTTP | `axios ^1.13` |
| Almacenamiento local | `@react-native-async-storage/async-storage ^3` |
| Gráficos | `react-native-svg ^15` (la dona y los íconos se dibujan en SVG) |
| Otros | `react-native-gesture-handler`, `reanimated ^4`, `safe-area-context`, `screens`, `expo-linear-gradient`, `expo-symbols`, `expo-status-bar` |
| Pruebas | `jest ^29` + preset `jest-expo ^55`, `@testing-library/react-native ^13` |
| TypeScript | `~5.9` |

Punto de entrada: `frontend/index.ts` → `registerRootComponent(App)` → `frontend/App.tsx`.

`App.tsx` monta, de afuera hacia adentro:
`ThemeProvider` → `ErrorBoundary nombre="App"` → `AuthProvider` → `ToastProvider` → (`StatusBar style="light"`, `AppNavigator`, `ToastContainer`).
El `AlcanceProvider` (Hogar/persona + periodo) **no** está aquí: lo monta `AppNavigator` solo cuando hay sesión.

### Cómo se arranca (desarrollo)

```bash
cd frontend
npm install            # el Dockerfile usa: npm ci --legacy-peer-deps
npm run web            # expo start --web   (también: npm start, npm run android, npm run ios)
```

### `EXPO_PUBLIC_API_URL`

- `src/services/api.ts`: `const BASE_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8820'`.
- Es la URL del **gateway** del backend (puerto 8820 por defecto). Todas las rutas de los services empiezan por
  `/AUTH-SERVICE/...` o `/BUDGET-SERVICE/...` (el gateway enruta por nombre de servicio en Eureka).
- Expo **incrusta** las variables `EXPO_PUBLIC_*` al construir: cambiarla exige reconstruir el bundle/imagen.
- En local puede ir en `frontend/.env` (está ignorado por git; no lo subas).

### Imagen web (`frontend/Dockerfile`)

Dos etapas:
1. `node:20-alpine` builder: `npm ci --legacy-peer-deps`, `COPY . .`, `ARG/ENV EXPO_PUBLIC_API_URL` (por defecto
   `http://localhost:8820`) y `npx expo export --platform web` → genera `dist/`.
2. `node:20-alpine` runtime: `npm install -g serve`, copia `dist/`, `EXPOSE 3000`, `CMD serve -s dist -l 3000`
   (`-s` = modo SPA: toda ruta desconocida devuelve `index.html`, necesario para las rutas legibles de §2.3).

En `docker-compose.yml` (raíz) el servicio `frontend` pasa `EXPO_PUBLIC_API_URL: ${API_URL:-<ip-de-la-LAN>:8820}`
y publica `3000:3000`. Para otro entorno, define `API_URL` al construir; no copies a la documentación la IP que
trae como valor por defecto.

`frontend/dist/` y `frontend/coverage/` existen en disco pero están en `.gitignore` (son artefactos).

---

## 2. Navegación

Archivos: `src/navigation/AppNavigator.tsx` y `src/navigation/linking.ts`.

### 2.1 Estructura

```
NavigationContainer (linking solo en web, documentTitle "<título> · Oh Churus", tema fijo dark:true)
├── (sin sesión) AuthStack  [native-stack, sin cabecera]
│     ├── Login      → screens/auth/LoginScreen.tsx
│     └── Register   → screens/auth/RegisterScreen.tsx
└── (con sesión) AlcanceProvider > RootStack [native-stack, sin cabecera]
      ├── MainTabs (nombre histórico; ya NO hay tabs) → ModuleDrawer [drawer, menú lateral "MenuFinanzas"]
      │     └── FinanceModule → FinanzasNavigator [UNA sola native-stack]
      │           ├── (sin cabecera nativa: dibujan su propio <Encabezado/>)
      │           │   Inicio, Presupuesto, Categoria, Cierre, PorConfirmar, Ingresos, Movimientos, Ahorros, Deudas
      │           └── Group con cabecera nativa (flecha atrás + título)
      │               Plan, ScheduledList, ScheduledForm, CategoryDrillDown, SettingsMain, ExportImport,
      │               PeriodConfig, CurrencyConfig, Household, CategoriesList, CategoryForm,
      │               AdminUsers, AdminRoles, AdminModules, AdminAudit
      ├── Anotar             (presentation: 'fullScreenModal')
      └── MovementFormModal  (presentation: 'modal', cabecera con título dinámico:
                              "Editar movimiento" | "Sub-gasto" | "Nuevo movimiento")
```

Antes de todo eso: si `AuthContext.loading` o no se sabe aún si se hizo el onboarding → `Spinner` a pantalla
completa; si el onboarding no está hecho → `OnboardingScreen` (no es una ruta, se pinta en lugar del navegador).

**Cada pantalla registrada va envuelta** por `pantalla(Componente)` → `ErrorBoundary` propio con botones
"Volver al inicio" (`irAlInicio`) y "Reintentar". El envoltorio se cachea en un `WeakMap` para no desmontar la
pantalla en cada render (si no, se perdería lo escrito). Si registras una pantalla nueva, **envuélvela con `pantalla()`**.

### 2.2 Tabla de rutas

| Ruta (name) | URL web | Archivo | Cómo se llega |
|---|---|---|---|
| `Login` | `/entrar` | `screens/auth/LoginScreen.tsx` | Sin sesión (o al caducar el token) |
| `Register` | `/registro` | `screens/auth/RegisterScreen.tsx` | Botón "¿No tienes cuenta? Regístrate" en Login |
| `Inicio` | `/` | `screens/inicio/InicioScreen.tsx` | Pantalla inicial; menú → Inicio; `irAlInicio()` |
| `Movimientos` | `/movimientos?categoriaId=` | `screens/movimientos/MovimientosScreen.tsx` | Inicio: tocar "Libre para gastar", **deslizar hacia arriba**, o en la hoja de una categoría "Ver sus movimientos" (con `categoriaId`); menú → Movimientos |
| `Presupuesto` | `/presupuesto` | `screens/presupuesto/PresupuestoScreen.tsx` | Inicio: tocar el **centro de la dona**; menú |
| `Categoria` | `/presupuesto/categoria/:categoryId` | `screens/presupuesto/CategoriaScreen.tsx` | Fila de Presupuesto; hoja de categoría en Inicio ("Ver su presupuesto"); un "liberado" en Movimientos |
| `Cierre` | `/cierre` | `screens/presupuesto/CierreScreen.tsx` | Botón en el encabezado de Presupuesto; menú "Cierre de cada mes" |
| `PorConfirmar` | `/por-confirmar?quien=` | `screens/pendientes/PorConfirmarScreen.tsx` | Inicio: recuadro "Por pagar" (`quien='DOS'` o id de persona) (con un punto si me toca confirmar algo); menú |
| `Ingresos` | `/ingresos` | `screens/ingresos/IngresosScreen.tsx` | Inicio: recuadro "Ingresos"; menú |
| `Ahorros` | `/ahorros` | `screens/ahorros/AhorrosScreen.tsx` | Inicio: recuadro "Ahorros"; menú |
| `Deudas` | `/deudas` | `screens/deudas/DeudasScreen.tsx` | Inicio: recuadro "Deudas"; menú |
| `Plan` | `/plan` | `screens/plan/PlanScreen.tsx` | Solo menú "Plan y quincenas" |
| `ScheduledList` | `/programados` | `screens/scheduled/ScheduledScreen.tsx` | Menú "Programados"; enlace en Deudas "Para cambiar una deuda, edítala en Programados" |
| `ScheduledForm` | `/programados/formulario` | `screens/scheduled/ScheduledFormScreen.tsx` | Desde ScheduledList (crear / editar) |
| `CategoryDrillDown` | `/movimientos/categoria/:categoryId` | `screens/summary/CategoryDrillDownScreen.tsx` | **Solo** desde CategoriaScreen ("Ver los movimientos de X") |
| `SettingsMain` | `/ajustes` | `screens/settings/SettingsScreen.tsx` | Menú "Ajustes" |
| `ExportImport` | `/ajustes/exportar` | `screens/settings/ExportImportScreen.tsx` | Ajustes |
| `PeriodConfig` | `/ajustes/dia-de-corte` | `screens/settings/PeriodConfigScreen.tsx` | Ajustes |
| `CurrencyConfig` | `/ajustes/moneda` | `screens/settings/CurrencyConfigScreen.tsx` | Ajustes |
| `Household` | `/ajustes/hogar` | `screens/settings/HouseholdScreen.tsx` | Ajustes |
| `CategoriesList` | `/ajustes/categorias` | `screens/categories/CategoriesScreen.tsx` | Ajustes |
| `CategoryForm` | `/ajustes/categorias/formulario` | `screens/categories/CategoryFormScreen.tsx` | Desde CategoriesList |
| `AdminUsers` / `AdminRoles` / `AdminModules` / `AdminAudit` | `/ajustes/admin/{usuarios,roles,modulos,auditoria}` | `screens/admin/*.tsx` | Ajustes, solo si `esAdmin && useMenu().puedeVer('AdminUsers')` |
| `Anotar` (raíz) | `/anotar?categoriaId=` | `screens/anotar/AnotarScreen.tsx` | Inicio (botones Gasto/Ingreso, gestos, hoja de categoría, "Más" de la dona), Ahorros (depositar / usar), Deudas ("Anotar compra" con `pagadoCon: 'tarjeta:<id>'`) |
| `MovementFormModal` (raíz) | `/movimiento` | `screens/movements/MovementFormScreen.tsx` | Tocar un movimiento en Movimientos / PorConfirmar / CategoryDrillDown (`{ movement }` = editar); "Más" en Anotar (`navigation.replace` con `{ prefill }`) |

### 2.3 Menú lateral (`MENU` en AppNavigator)

Se abre con el ícono ☰ de `Encabezado` (`DrawerActions.openDrawer()`) o deslizando desde el borde (drawer).
Tres grupos: **[Inicio, Ahorros, Deudas, Presupuesto, Cierre de cada mes, Por confirmar, Ingresos]**,
**[Plan y quincenas, Programados, Movimientos]**, **[Ajustes]**.
Al elegir un destino se hace `CommonActions.reset` de la pila de finanzas a `[Inicio, destino]`: así la flecha
atrás siempre lleva al Inicio, no a la última pantalla mirada.

### 2.4 Rutas legibles en la web (`linking.ts`)

- `linking.enabled` solo en web. `RUTAS` es la tabla de §2.2; `initialRouteName: 'Inicio'` deja el Inicio debajo
  cuando se entra directo por URL.
- `sinParametrosObjeto()` quita de la URL los parámetros que son objetos (p. ej. `movement`, `prefill`): al
  recargar esas pantallas abren su forma por defecto (formulario nuevo).
- Parsers: `categoriaId`/`categoryId` solo números (si no, `undefined`); `quien` acepta número o texto (`MIOS`, `DOS`).
- `irAlInicio(navigation)` y `volverOInicio(navigation)` (atrás si hay a dónde; si no, Inicio). Úsalos en vez de
  `goBack()` a secas en pantallas a las que se puede entrar por URL.

### 2.5 Carpetas de pantallas: ¿cuál se usa?

**Todas las carpetas de `src/screens/` están registradas.** No hay pantallas huérfanas, pero sí nombres confusos
y solapes:

| Carpeta | Estado real |
|---|---|
| `movimientos/` (MovimientosScreen) | **La lista de movimientos actual** (estilo Monefy). |
| `movements/` (MovementFormScreen) | **El formulario completo** de un movimiento (crear/editar/borrar), abierto como modal `MovementFormModal`. No es un duplicado de `movimientos/`: es otra cosa con nombre parecido. |
| `summary/` (CategoryDrillDownScreen) | Resto de la antigua pestaña "Resumen". Sigue registrada y solo se llega desde CategoriaScreen. **Solapa** con `Movimientos?categoriaId=` (que es lo que usa el Inicio). Tipada contra un `SummaryStackParamList` que ya no existe. No sigue el selector Hogar/persona. |
| `scheduled/` (Programados) | En uso, desde el menú y desde Deudas. Pantalla "de las de siempre" (cabecera nativa, estilo previo). |
| `categories/` | En uso, solo desde Ajustes. |
| `settings/`, `admin/`, `plan/` | En uso, estilo previo (cabecera nativa, `Card`, `Button`). |
| `inicio/`, `anotar/`, `presupuesto/`, `pendientes/`, `ingresos/`, `ahorros/`, `deudas/` | Las pantallas "nuevas" (olas 6–7), con `Encabezado` propio. |

---

## 3. Pantallas

Convenciones comunes de las pantallas nuevas:
- Cargan con `useCarga(traer)` + `useFocusEffect(() => cargar())` (se recargan cada vez que la pantalla gana foco)
  y `exigir(respuesta)` (lanza si `correct === false`). Muestran `Spinner`, `EstadoError` (con "Reintentar") o el
  contenido; nunca una lista vacía cuando en realidad hubo un error.
- Pull‑to‑refresh con `RefreshControl` → `refrescar`.
- La barra superior es `components/molecules/Encabezado.tsx`: ☰ (o ← con `atras`), título + **selector de alcance**
  (Hogar / cada persona, `conAlcance`) y **periodo con flechas** (`conPeriodo`). Cambiar alcance o periodo ahí lo
  cambia **en toda la app** (vive en `AlcanceContext`). Se puede avanzar como mucho 3 periodos al futuro.

### 3.1 InicioScreen (`screens/inicio/InicioScreen.tsx`) — la pantalla principal, estilo Monefy

**Datos** (todo en `traer`):
- `estadoService.periodo(corte, periodo.inicio, alcance)` → `POST /BUDGET-SERVICE/oh-churus/v1/estado/periodo`
  (la **misma fuente** que Presupuesto, para que las cifras no se contradigan).
- `dashboardService.getPending(userId, corte, periodo.inicio)` → `/dashboard/pending` (pagos por confirmar).
- `categoryService.getAll({ size: 200 })` → `/categories/all` (para poner alrededor de la dona también las
  categorías de gasto sin movimiento; en Hogar solo las que tienen `householdId`). Un fallo aquí no tumba la pantalla.
- `deudaService.todas()` → `/debts/all`, filtrado por `deudasDelAlcance()`. Si falla, el recuadro dice
  "No se pudieron cargar" y el Inicio **igual carga** (hay prueba).

**Qué muestra:**
1. `Encabezado` (☰, selector de alcance, periodo con flechas). El periodo **se cambia con las flechas**; la dona
   tiene props `onAnterior/onSiguiente` (deslizar para cambiar periodo) pero **Inicio no se las pasa**.
2. **`DonaDeCategorias`** (`components/molecules/DonaDeCategorias.tsx`):
   - Porciones = lo **gastado** por categoría (`kind === 'EXPENSE'` → `actual`). Las categorías de ahorro
     (`SAVING`) van alrededor con valor 0 (para depositar con un toque).
   - Alrededor van **todas** las categorías (hasta `MAXIMO_ICONOS = 16`; si hay más, el último lugar es "Más" →
     `onMas` abre Anotar gasto). Las que tienen gasto siempre están entre las visibles.
   - `ubicarCategorias()`: lugares fijos y parejos; cada categoría con gasto se sienta en el lugar libre más cercano
     a **su** porción (la más grande elige primero); las sin gasto llenan el resto. Orden: `ordenarPorGasto`
     (más gastado primero; empates/sin gasto por nombre `localeCompare('es')`).
   - Cada ícono con gasto lleva una pastilla con su % (`porcentaje()`: `<1%` en vez de `0%`). Sin gasto → opacidad 0.4.
   - Línea delgada de la porción al ícono que rodea la dona sin cruzarla.
   - Colores: `categoryColor` o `PALETA[i]`; el ícono se pinta con `tintaSobre(color)`.
   - **Centro de la dona**: ingresos recibidos (verde) y **gastos del mes** (rojo), "ingresos · gastos", y si hay,
     `+ $X con ahorros`. El tamaño de letra se ajusta al ancho pero **nunca baja de 14**; si no cabe usa
     `formatCompacto`. Tocar el centro → `Presupuesto`.
   - **Lo pagado con ahorros (ola 7.5):** `gastosDelMes = max(0, totals.spent − totals.fromSavings)`. Lo pagado
     con un ahorro **sí** aparece en la porción de su categoría (es gasto del carro, p. ej.), pero **no** en el rojo
     del centro, porque no salió del sueldo. Se informa aparte como "+ $X con ahorros" y en la etiqueta accesible.
3. Tocar un ícono abre una **hoja** (Modal) con: "Anotar aquí" (`Anotar` con `tipo EXPENSE` y `categoriaId`),
   "Ver sus movimientos" (`Movimientos` con `categoriaId`), "Ver su presupuesto" (`Categoria`).
4. **Cuatro recuadros** (2×2): Ingresos (recibido "de" esperado → `Ingresos`), Por pagar (`totals.toPay`, la misma
   cifra que descuenta "Libre para gastar"; detalle "N pagos + previstos" → `PorConfirmar`), Ahorros
   (`totals.savingsBalance` → `Ahorros`), Deudas (total y cuota del mes → `Deudas`). Valores con `formatCompacto`.
5. Ya no hay aviso aparte de "pagos por confirmar" (ola 7.6): el recuadro **Por pagar** dice "N por confirmar" y muestra un punto si hay pagos **míos** (en Hogar) o de **esa** persona por confirmar. **Deudas** dice "cuotas del mes X", "en tarjetas" o "nada anotado".
6. Barra fija "Libre para gastar · ver movimientos" (`totals.freeToSpend`, verde/rojo) → `Movimientos`.
7. Dos botones grandes: **Gasto** (rojo) y **Ingreso** (verde) → `Anotar`.

**Gestos (`PanResponder` sobre toda la pantalla, `testID="inicio-gestos"`):**
- Captura si `|dx| > 30` y `|dx| > 1.8·|dy|` (horizontal), o si `dy < −40`, `|dy| > 2·|dx|` **y** (la lista ya está
  abajo del todo **o** el gesto empezó en los 200 px inferiores — zona de "Libre" y botones).
- Al soltar: `dx > 80` → **Anotar gasto**; `dx < −80` → **Anotar ingreso**; `dy < −90` → **Movimientos**.
- `onPanResponderTerminationRequest: () => false` (no suelta el gesto).
- En web: `touchAction = 'pan-y'` en la raíz, `'none'` en la zona de abajo y en la barra de saldo, y
  `overscrollBehaviorX = 'none'` en `html`/`body` para que Chrome no interprete el deslizar a la derecha como
  "página anterior". Si tocas el layout de Inicio, conserva estos refs (`raiz`, `zonaAbajo`, `zonaSaldo`).
- No hay prueba automática de los gestos: verifícalos a mano en el celular.

### 3.2 MovimientosScreen (`screens/movimientos/MovimientosScreen.tsx`)

**Datos:** `movementService.getAll({ startDate, endDate, size: 500 })` (`/movements/all`),
`categoryService.getAll` (`/categories/all`) y `estadoService.periodo` (solo para los **liberados**, que ya no son
movimientos activos pero se siguen mostrando tachados; su fallo se ignora).

**Filtros:**
- Pestañas **Gastos / Ingresos / Todos** (`tipo`; por defecto `EXPENSE` o `route.params.tipo`).
- Con Gastos o Ingresos: vista **Por categoría** (por defecto) o **Por fecha** (`route.params.vista`). **Todos**
  fuerza la vista por fecha (`porFecha = vista === 'FECHA' || tipo === 'TODOS'`).
- Alcance: Hogar → movimientos con `shared !== false` y categorías con `householdId`; persona → los de ese `userId`.
- Se excluyen inactivos y **sub-gastos** (`parentMovementId`: ya los cuenta el padre).
- **Buscador** por nota o nombre de categoría, sin mayúsculas ni tildes (`normalizar` con NFD). Buscando, solo
  quedan las categorías con coincidencias y se ven ya desplegadas. (Reemplaza a la antigua pantalla "Buscar".)

**Vista por categoría:**
- Un grupo por cada categoría visible del tipo (aunque no tenga movimientos: aparece "Sin movimientos", apagada,
  no desplegable).
- `total` = solo lo **confirmado**; lo no confirmado va aparte como `+ $X por pagar / por recibir`.
- **Orden**: `b.total − a.total` y luego `a.nombre.localeCompare(b.nombre, 'es')` → **la más gastada primero,
  luego alfabético** (las vacías quedan al final, ordenadas por nombre). Hay prueba.
- Resumen de cada fila: "N pagados · M por pagar · P% · K liberados".
- Botón **"Expandir todo" / "Contraer todo"** (solo en vista por categoría, sin búsqueda y si hay grupos con
  movimientos). Si llega `categoriaId`, esa categoría abre desplegada.
- Liberados: fila tachada "liberado, no se pagó · toca para deshacer" → `Categoria` (ahí se deshace).

**Vista por fecha / Todos:** movimientos agrupados por día (cabecera `diaLargo`: "lun 10 nov") con el neto del día;
en cada fila la **nota en negrita adelante** (si no hay nota, el nombre de la categoría), y debajo categoría ·
"por pagar" · medio de pago (`comoSePago`: "Tarjeta X · cuota 1/6", "Con el Ahorro", …) · quién pagó (en Hogar).
En **Todos** la cabecera muestra `+entradas  −salidas`, donde las salidas **excluyen** lo pagado con ahorros
(`fundedFromCategoryId`) y se dice aparte "y $X pagados con ahorros" (ola 7.5).

Tocar un movimiento → `MovementFormModal` con `{ movement }` (editar).

### 3.3 AnotarScreen (`screens/anotar/AnotarScreen.tsx`) — teclado rápido, modal a pantalla completa

Parámetros: `tipo` (`EXPENSE`|`INCOME`), `categoriaId` (viene elegida desde la dona/Ahorros), `pagadoCon`
(`'ahorro:<id>'` o `'tarjeta:<id>'`).

- Cabecera de color (rojo gasto, verde ingreso, `VERDE_AHORRO #1FA67A` si es depositar en un ahorro) con ✕,
  título, **pastilla de fecha** ("Hoy"/"Ayer"/dd/mm → `Calendario`) y el monto grande. El texto de la cabecera usa
  `textoLegibleSobre(color)` por contraste.
- **Teclado propio** (`components/molecules/Teclado.tsx` + `utils/calculadora.ts`): suma/resta/multiplica/divide de
  verdad (`evaluar`, `mostrar`, `pulsar`, `tieneOperacion`); muestra "= $total" si hay operación. No hay `TextInput`
  detrás, así que no sale el teclado del celular. En escritorio, `useTecladoFisico`: dígitos, operadores, Retroceso,
  **Enter = anotar**, **Escape = cerrar** (salvo con foco en un campo de texto).
- **Categoría**: campo que abre una hoja con buscador (sin tildes), "Recientes" (las últimas 6 en AsyncStorage
  `oc.recientes.<tipo>`), sección Gastos/Ingresos y sección **"Ahorros (meter plata)"** con su saldo. Si no vino
  elegida, **no se preselecciona ninguna** (antes un gasto podía quedar como ahorro). Chips con 3 recientes.
- **"Pagado con"** (solo gasto que no sea depósito, y si hay ahorros o tarjetas): Fondos del mes / un ahorro
  (`fundedFromCategoryId`; aviso de cómo queda el saldo, en ámbar si queda negativo; "No baja lo libre del mes") /
  una tarjeta (`cardId` + `installments`, chips de cuotas 1·3·6·12·24·36; con tarjeta el movimiento se guarda
  `confirmed: true` y desaparece el selector pagado/por pagar).
- **La nota está SIEMPRE visible** (`TextInput` con placeholder "Nota: ¿en qué fue? (ej: almuerzo con Samy)",
  `accessibilityLabel="Nota del movimiento"`, `maxLength 255`). Es lo que dice en qué se gastó y es lo que se lee
  primero en Movimientos por fecha. **No la escondas detrás de "Más" ni la muevas a una hoja**: para los usuarios es
  lo más importante de esta pantalla.
- Segmento **Ya pagado / Por pagar** y chip **"Más"** → `navigation.replace('MovementFormModal', { prefill })`
  pasando TODO lo escrito (monto, categoría, tipo, fecha, nota, confirmado, ahorro o tarjeta + cuotas).
- Botón del teclado: "Anotar $X" / "Depositar $X" / "Anotar · elige la categoría"; deshabilitado sin monto o sin categoría.
- Guardar: `movementService.save` (`/movements/save`), recuerda la reciente, toast ("Gasto anotado",
  "Depositado en el ahorro", "Ingreso anotado", o "N cuotas de $X") y `goBack()`.
- **Salir con algo escrito pregunta** (`ConfirmarDescarte`), también con atrás del navegador/celular
  (`beforeRemove`). Lo que llegó preelegido (categoría, medio) no cuenta como "escrito".

### 3.4 MovementFormScreen (`screens/movements/MovementFormScreen.tsx`) — formulario completo (modal)

Params: `movement` (editar), `prefill` (desde Anotar "Más"), `parentMovement` (sub-gasto). Tipo, monto (teclado),
categoría, fecha (`Calendario`), nota, pagado/por pagar, "Pagado con" (ahorro/tarjeta + cuotas), borrar con
confirmación (`movementService.delete` → `/movements/delete/{id}`), guardar con `useAccionUnica` (evita doble toque).
Pregunta antes de descartar cambios; Escape cierra en escritorio. Tiene la única excepción de la regla de tamaño
de fuente (`statusDot`, un punto de color de 8×8).

### 3.5 DeudasScreen (`screens/deudas/DeudasScreen.tsx`) y sus formularios

Archivos auxiliares en la misma carpeta: `Hoja.tsx` (hoja inferior genérica con Cancelar/Confirmar; reemplaza a
`Alert`/`window.confirm`), `FormTarjeta.tsx`, `FormPagoTarjeta.tsx`, `FormDeuda.tsx`, `fechas.ts` (`diaMes` "6 nov",
`mesAnio` "mar 2027").

- Datos: `deudaService.todas()` → `/debts/all`, filtrado con `utils/deudas.ts::deudasDelAlcance` (sigue el selector
  Hogar/persona). `Encabezado` sin periodo.
- Cabecera "Deben $X · este mes en cuotas $Y".
- **Tarjetas**: cada una desplegable con sus compras por pagar; acciones **Anotar compra**
  (`Anotar` con `pagadoCon: 'tarjeta:<id>'`), **Editar**, **Pagar tarjeta**, **Borrar** (con `Hoja` de
  confirmación → `/cards/delete/{id}`). "Agregar tarjeta" → `FormTarjeta` (`/cards/save`; nombre ≤ 60, día de pago
  y día de corte 1–31, color de `COLORES_TARJETA`).
- **Pago de tarjeta (`FormPagoTarjeta`, ola 7.2)**: se paga **el monto y la fecha que se quiera**.
  - Monto por defecto = `pagableHoy(t)` = `payableNow` (o `dueNow + currentCycle`), redondeado. Chip "Todo: $X".
  - Fecha por defecto hoy; chip "Fecha: …" abre `Calendario` con `maximo = hoy` (no se paga en el futuro).
  - `validarPago`: > 0; y **si la fecha es hoy**, no más de lo que se debe hoy.
  - Envía `deudaService.pagarTarjeta(id, { amount, upTo: fecha })` → `POST /cards/pay/{id}`. Toast "Pago anotado".
  - Texto fijo: "No es un gasto nuevo: las compras ya contaron cuando las hiciste."
- **Deudas en cuotas (créditos)**: barra de cuotas pagadas/total, **Adelantar una cuota** (`/debts/advance/{scheduledMovementId}`,
  con `Hoja`). "Nueva deuda en cuotas" → `FormDeuda` → crea un **programado** (`scheduledService.save`,
  `frequency MONTHLY`, `kind PAYMENT`, `durationMonths`, `dayOfMonth`, `startDate = inicioDeLaDeuda(dia)`).
  Editar una deuda se hace en Programados.
- Patrón `correr(hacer, exito, cerrar)`: un fallo se dice con toast y la hoja **queda abierta**.

### 3.6 PlanScreen (`screens/plan/PlanScreen.tsx`) — "Plan y quincenas"

Cuatro vistas con `CapsuleToggle`: **Quincena** (por defecto), **Calendario**, **Hogar**, **Proyección**. Todo lo
calcula el servidor (`planService`, `/BUDGET-SERVICE/oh-churus/v1/plan/...`), la pantalla no suma nada a mano.
- **Quincena** (`/plan/quincena`): ventana actual y "Mi próxima" (si dura > 20 días se titula "mes de salario"):
  ingresos, descuentos de nómina, qué debo pagar, ahorro, plata que debo separar, y **"Queda libre" / "No alcanza:
  faltan"**. Avisos (`VENCIDO`, `PROXIMO`, `CUOTA_TERMINA`, `QUINCENA_AJUSTADA`) pasados por `humanizarAviso`.
  "Pendiente de antes" y "Cuotas" (faltan N de M). "Disponible para gastar" ≠ saldo del banco.
- **Calendario** (`/plan/calendar` from/to del mes; flechas ‹ › por mes).
- **Hogar** (`/plan/responsibilities`): "Quién paga qué".
- **Proyección** (`/plan/projection`, 3 periodos).
- Estados de un ítem: PAGADO, PENDIENTE, VENCIDO, PREVISTO, PROVISION ("Plata separada"), LIBERADO (tachado). Un
  estado desconocido se pinta como PREVISTO (no tumba la pantalla).
- Ojo: usa `user.budgetStartDay` y su propio mes; **no sigue el selector de alcance ni el periodo del Encabezado**
  (tiene cabecera nativa, no `Encabezado`). Los íconos de estado son emojis.

### 3.7 PresupuestoScreen (`screens/presupuesto/PresupuestoScreen.tsx`)

- Pestañas **Planeado / Real (por defecto) / Quién**, sobre la misma lista de categorías de `estado/periodo`.
  El total de arriba es la suma de la lista.
- Orden: de la que más (planeado en Planeado, real en Real) a la que menos, empates por nombre.
- Cada fila: real de asignado y estado (`utils/estadoTextos.ts`: DENTRO, EN_RIESGO, EXCEDIDO, SIN_PRESUPUESTO,
  META_CUMPLIDA, META_INCUMPLIDA, EN_CURSO) → `Categoria`.
- **Presupuestar otra categoría** (hoja con categorías; monto con `MontoModal` → `/budget-allocation/save`) y
  **Copiar el presupuesto del periodo anterior** (`/budget-allocation/copy-previous`). Se puede presupuestar una
  categoría que ya tiene gastos. Botón a la derecha del encabezado → `Cierre`.
- `CategoriaScreen`: lo planeado, gastado, por pagar y disponible; elementos del plan (factura, cuota, ahorro,
  bolsa) con quién paga y estado; quién gastó cuánto; fijar/cambiar/quitar lo presupuestado con el teclado de la app
  (`/budget-allocation/{list,save,delete}`); **deshacer una liberación** (`/movements/unrelease/{id}`); "Ver los
  movimientos" → `CategoryDrillDown`.
- `CierreScreen`: presupuestado vs real por categoría, de los últimos 6 periodos (`/estado/historial`), con una
  frase "como la diría una persona" (`fraseDelCierre`).

### 3.8 IngresosScreen (`screens/ingresos/IngresosScreen.tsx`)

Desde `estado/periodo`: Esperado / Recibido / Falta, por persona y por fecha de pago (incluye ingresos sueltos sin
`scheduledMovementId`, para que la lista sume lo mismo que el encabezado). Los descuentos de nómina van aparte.

### 3.9 AhorrosScreen (`screens/ahorros/AhorrosScreen.tsx`)

Desde `estado/periodo` (categorías `kind === 'SAVING'`): total ahorrado, y por ahorro saldo acumulado, lo que entró
y lo que se usó este mes. Botones **Depositar** (`Anotar` con `categoriaId` del ahorro: es un gasto en la categoría
de ahorro, en verde) y **Usar** (`Anotar` con `pagadoCon: 'ahorro:<id>'`: un gasto pagado con el ahorro, que no
baja lo libre del mes). Verde de ahorro distinto del verde de ingresos.

### 3.10 PorConfirmarScreen (`screens/pendientes/PorConfirmarScreen.tsx`)

- Datos: `/dashboard/pending`. Filtro `quien`: `'MIOS'` (por defecto), `'DOS'` (de los dos) o el id de una persona.
  `Encabezado` sin selector de alcance (tiene su propio filtro).
- Pagos primero, ingresos esperados aparte (para que el conteo coincida con el recuadro del Inicio).
- Marcar varios (y "Marcar todos", que marca, no filtra) → **Confirmar N pagos por $X**
  (`/movements/confirm-batch`, con monto cambiado si se editó). Si el servidor rechaza el lote, no se da nada por confirmado.
- Tocar una fila abre opciones: cambiar el valor (`MontoModal`), **Liberar** plata (`/movements/release/{id}` con
  monto: "este mes no ahorro"), **pasarle el pago a otra persona** (`/movements/reassign/{id}`), editar
  (`MovementFormModal`).

### 3.11 Ajustes y Administración

- `SettingsScreen`: avatar con inicial, nombre y correo; tarjetas a Exportar a Excel, Día de corte, Moneda,
  Hogar, Categorías; **Modo claro/oscuro** (`toggleTheme`); Administración (si `esAdmin` y `useMenu().puedeVer`);
  Cerrar sesión con `confirmarAccion`.
- `ExportImportScreen`: **solo exporta** (la importación CSV se retiró; el nombre se conserva porque el menú del
  backend apunta a esa ruta). `POST /BUDGET-SERVICE/oh-churus/v1/export/excel` con `responseType: 'blob'`, se
  descarga como `Presupuesto_<inicio>_<fin>.xlsx`; un error llega como Blob JSON y se lee. Usa `PeriodNavigator`.
  Es la única pantalla que llama `api` directamente (sin service).
- `PeriodConfigScreen`: día de corte → `authService.updateUser` (`/users/save`, exige nombre y correo).
- `CurrencyConfigScreen`: moneda/decimales con vista previa; lee `preferenciasService.mias()`
  (`/AUTH-SERVICE/oh-churus/v1/preferences/mine`) y guarda con `authService.updateUser`; aplica al momento con
  `configurarFormatoDeMoneda`.
- `HouseholdScreen`: crear hogar, invitar por correo, sacar a un miembro, salir del hogar (confirmación dentro de
  la app) → `/household/{by-user,create,invite,remove-member}` + `authService.getUser` para nombres.
- `CategoriesScreen` (árbol, `/categories/tree`) y `CategoryFormScreen` (nombre, tipo, ícono `IconPicker`, color
  `ColorPicker`, padre `ParentCategoryPicker`, personal/compartida; evita duplicados por mayúsculas/tildes;
  borrar con confirmación; `/categories/{all,save,delete}`).
- `admin/`: `AdminUsersScreen` (roles, activar/desactivar; muestra el "no" del backend), `AdminRolesScreen`
  (ADMIN y USER no se renombran ni retiran), `AdminModulesScreen` (rejilla rol × módulo con 4 permisos),
  `AdminAuditScreen` (solo lectura, ventanas de días). Todas contra `/AUTH-SERVICE/oh-churus/v1/admin/...`.
  La seguridad la pone el backend (403); el front solo esconde.

### 3.12 Onboarding (`screens/onboarding/OnboardingScreen.tsx`)

5 pasos con emoji, "Siguiente"/"Saltar". Al terminar guarda `@oh_churus_onboarding_done = 'true'` en AsyncStorage
y `AppNavigator` deja de mostrarlo. Exporta `isOnboardingDone()`. Sus textos son antiguos (hablan de "salud, hábitos").

### 3.13 Auth (`screens/auth/`)

- `LoginScreen`: correo + contraseña → `useAuth().login` (`/AUTH-SERVICE/oh-churus/v1/auth/login` y luego
  `/users/get/{id}` para `budgetStartDay`). Muestra el aviso "Tu sesión expiró…" (`AVISO_SESION_EXPIRADA`) si se
  cerró por 401, y el mensaje del servidor en un 429 (demasiados intentos).
- `RegisterScreen`: nombre, correo, contraseña (`validatePasswordSegura`: 8+ caracteres, una letra y un número) →
  `/auth/register`.

---

## 4. Services (`src/services/`)

Todos usan la instancia `api` (axios) y **todas las llamadas son `POST`**. Devuelven `ResultDTO<T>`
(`{ correct, message, errorCode, object }`); las listas paginadas traen `object.list`. Prefijos:
`AUTH = /AUTH-SERVICE/oh-churus/v1`, `BUDGET = /BUDGET-SERVICE/oh-churus/v1`.

| Archivo | Funciones → endpoint |
|---|---|
| `api.ts` | Instancia axios (`timeout 15000`). Interceptor de request: `Authorization: Bearer <token>` de AsyncStorage `@oh_churus_token`. Interceptor de response: traduce al español los `message` de `correct:false` (`utils/mensajes.ts::enEspanol`); en error, **401 → cerrar sesión ('EXPIRADA')**, **403 sin token → cerrar ('SIN_SESION')**, 403 con token (admin) no cierra; login/register exentos. `registrarCierreDeSesion(fn)` lo registra AuthContext. |
| `authService.ts` | `login` → `AUTH/auth/login`; `register` → `AUTH/auth/register`; `getUser(id)` → `AUTH/users/get/{id}`; `updateUser` → `AUTH/users/save` |
| `adminService.ts` | `adminService`: `usuarios` → `admin/users/all`; `usuario(id)` → `admin/users/get/{id}`; `crearUsuario` → `admin/users/save`; `fijarRoles` → `admin/users/{id}/roles`; `cambiarActivacion` → `admin/users/{id}/{activate|deactivate}`; `roles` → `admin/roles/all`; `guardarRol` → `admin/roles/save`; `desactivarRol` → `admin/roles/delete/{id}`; `modulos` → `admin/modules/all`; `permisosDeRol` → `admin/permissions/{role}`; `guardarPermiso` → `admin/permissions/save`; `auditoria` → `admin/audit/all`. `menuService.mio` → `AUTH/menu/mine`. `preferenciasService`: `mias` → `AUTH/preferences/mine`; `configuracion` → `AUTH/admin/config/all`; `guardarAjuste` → `AUTH/admin/config/save` |
| `budgetAllocationService.ts` | `save`, `list`, `copyPrevious` (`copy-previous`), `delete/{id}` → `BUDGET/budget-allocation/...` |
| `categoryService.ts` | `save`, `getAll` (`all`), `getTree` (`tree`), `delete/{id}` → `BUDGET/categories/...` |
| `dashboardService.ts` | `getPending` → `BUDGET/dashboard/pending` |
| `deudaService.ts` | `todas` → `BUDGET/debts/all`; `adelantarCuota` → `debts/advance/{scheduledMovementId}`; `misTarjetas` → `BUDGET/cards/mine`; `guardarTarjeta` → `cards/save`; `pagarTarjeta(id, {amount, upTo})` → `cards/pay/{id}`; `borrarTarjeta` → `cards/delete/{id}`. Tipos `Deudas`, `Tarjeta`, `DeudaEnCuotas`. |
| `estadoService.ts` | `periodo` → `BUDGET/estado/periodo`; `historial` → `estado/historial`. Cuerpo: `{ budgetStartDay, referenceDate, scope: 'HOGAR'|'PERSONA', personUserId }`. Tipos `Alcance`, `EstadoDelPeriodo`, `ElementoDelPlan`, `EstadoCategoria`. **Es la fuente única de Inicio, Presupuesto, Categoría, Ingresos, Ahorros.** |
| `householdService.ts` | `create`, `addMember` (`add-member`), `inviteByEmail` (`invite`), `removeMember` (`remove-member`), `setDisplayName` (`display-name`), `getByUser` (`by-user`) → `BUDGET/household/...` |
| `movementService.ts` | `save`, `getAll` (`all`), `delete/{id}`, `reassign/{id}`, `release/{id}`, `unrelease/{id}`, `confirm-batch`, `confirm/{id}` → `BUDGET/movements/...` |
| `planService.ts` | `quincena`, `calendar`, `responsibilities`, `projection` → `BUDGET/plan/...` |
| `scheduledService.ts` | `save`, `getAll` (`all`), `delete/{id}`, `generatePending` (`generate-pending`), `materialize` → `BUDGET/scheduled/...` |
| *(sin service)* | `ExportImportScreen` → `BUDGET/export/excel` (blob) |

Funciones de service **sin uso en pantallas**: `adminService.usuario`, `adminService.crearUsuario`,
`preferenciasService.configuracion`, `preferenciasService.guardarAjuste`, `householdService.addMember`,
`householdService.setDisplayName`.

---

## 5. Contextos y hooks

### Contextos (`src/contexts/`)

| Contexto | Qué guarda / expone |
|---|---|
| `ThemeContext` | `{ colors, isDark, toggleTheme }`. Empieza **oscuro**; persiste en AsyncStorage `@oh_churus_theme` (`'dark'|'light'`). `colors` = `darkColors` o `lightColors` (`theme/colors.ts`). |
| `AuthContext` | `{ user, token, loading, isAuthenticated, esAdmin, login, register, logout, updateUser, avisoDeSesion, limpiarAvisoDeSesion }`. Guarda `@oh_churus_token` y `@oh_churus_user`. Al arrancar restaura la sesión y refresca el usuario (`budgetStartDay`); un 401 ahí borra la sesión. Registra el cierre automático en `api.ts`. Con token pide `preferenciasService.mias()` una vez y llama `configurarFormatoDeMoneda`. `esAdmin = roles.includes('ADMIN')` (**solo para pintar**). |
| `AlcanceContext` (modo personal / hogar) | `{ alcance, setAlcance, miembros, nombreDe, etiquetaAlcance, corte, periodo{inicio,fin}, anterior, siguiente, puedeSeguir, irAHoy, recargarMiembros }`. `alcance` = `{tipo:'HOGAR'}` o `{tipo:'PERSONA', userId}`; **al entrar se ve lo propio** (la persona logueada). `corte = user.budgetStartDay || 1`; el periodo se recalcula si cambia el corte; máximo 3 periodos al futuro. Miembros desde `householdService.getByUser`. `useAlcance()` lanza fuera del proveedor; `useAlcanceOpcional()` no. |
| `ToastContext` | `showToast(tipo, título, detalle)` / `hideToast`; tipos `success|error|warning|info`. Lo pinta `organisms/ToastContainer`. En pruebas está mockeado globalmente (`globalThis.__mockShowToast`). |

### Hooks (`src/hooks/`)

| Hook | Para qué |
|---|---|
| `useCarga(traer)` | Estados cargando / refrescando / error + `cargar` / `refrescar`. Exporta `exigir(res)` (devuelve `object` o lanza con el `message` del backend), `errorDeCarga`, `mensajeDeError`, `MENSAJE_ERROR_POR_DEFECTO`. Patrón obligatorio para pantallas que cargan datos. |
| `useAccionUnica(accion)` | Cerrojo síncrono (ref) contra el doble toque en "Guardar". Usado en MovementForm, ScheduledForm, CategoryForm. |
| `useAhorros()` | Lista de ahorros (`kind SAVING` de `estado/periodo`) con saldo **en el alcance actual**. Fuera del proveedor devuelve `[]`. |
| `useMisTarjetas()` (mismo archivo) | `deudaService.misTarjetas()`; para "Pagado con". |
| `useMenu()` | Menú permitido por el servidor (`menuService.mio`); `puedeVer(ruta, respaldo)`. Si falla, cae al rol del token. No decide seguridad. |

---

## 6. Componentes y reglas de diseño que se repiten

### Componentes (`src/components/`)

- **atoms**: `Text` (AppText; variantes `title` 35, `subtitle` 22, `body` 18, `caption` 14, `label` 16), `Button`,
  `Card`, `Input`, `Spinner`, `Toast`, `Icono` (íconos de línea en SVG por clave; si la clave no existe cae al emoji
  de `utils/iconMap.ts`), `CirculoCategoria`.
- **molecules**: `DonaDeCategorias`, `Encabezado`, `Teclado` (+ `useTecladoFisico`), `Calendario` (+ `moverDias`),
  `MontoModal` (monto con el Teclado), `ConfirmarDescarte`, `EstadoError`, `EmptyState`, `CapsuleToggle`,
  `PeriodNavigator`, `MovementItem`, `CategoryItem`, `ColorPicker`, `IconPicker`, `ParentCategoryPicker`.
- **organisms**: `ErrorBoundary`, `ToastContainer`.

### Reglas

1. **Tamaño mínimo de fuente 14.** `theme/typography.ts`: `xs 14, sm 16, md 18, lg 22, xl 26, xxl 35, title 44`
   (monótona). La prueba `NadaSeEscribeMasPequeno` lee todos los `.tsx` de `screens/`, `components/` y `navigation/`,
   busca `fontSize: <n>` y falla si `n < 14` (fichero:línea). Excepción única documentada:
   `MovementFormScreen.tsx:statusDot`. También comprueba que las excepciones existen y que la escala empieza en 14.
   Cuando un texto no cabe: `numberOfLines`, `adjustsFontSizeToFit` o `formatCompacto`, nunca encoger.
2. **Accesibilidad.** Todo `TouchableOpacity`/`Pressable` en `screens/`, `components/`, `navigation/` debe tener
   `accessibilityLabel` (o `accessible={false}` explícito si hereda el nombre). Lo exige `NadaInteractivoSinNombre`.
   Además se usan `accessibilityRole` (`button`, `tab`, `radio`), `accessibilityState` (`selected`, `expanded`,
   `disabled`) y etiquetas que dicen la cifra completa con `formatCurrency` aunque en pantalla salga compacta.
   Objetivos táctiles de ≥ 44 px (`minHeight: 44`/`52`).
3. **Tema claro/oscuro.** Nunca colores fijos para texto/fondo: `const { colors } = useTheme()`. Claves:
   `background, surface, surfaceLight, card, border, text, textSecondary, textMuted, primary (ámbar), secondary,
   income (verde), expense (rojo), pending, warning, danger…`. Texto sobre un color de fondo: `textoLegibleSobre(c)`
   (`theme/contraste.ts`, WCAG) o `tintaSobre(c)` (`utils/tinta.ts`). `ElContrasteSeMide` mide cada color de
   contenido contra cada fondo de ambos temas (4.5:1 texto, 3:1 superficies): un color nuevo de bajo contraste rompe la prueba.
4. **Dinero**: `formatCurrency(n)` (`utils/format.ts`): usa la preferencia del servidor (por defecto COP, 0 decimales,
   `es-CO`), conserva el signo negativo. `formatCompacto(n)` para recuadros estrechos ("$7,96 M", "$85 mil").
   Para leer lo que escribe la persona: `parseMonto("1.500.000")` (nunca `parseFloat`).
5. **Fechas**: `fechaLocalISO()` para "hoy" (**nunca `toISOString`**, que en Colombia por la noche ya es mañana);
   `formatDateShort` → `dd/mm`; `formatDate` → "6 Nov 2026"; `fechaHumana` → "1 nov" (año solo si no es el actual);
   `humanizarAviso` para textos del servidor; `plural(n, 'pago')`. Periodos: `utils/periodUtils.ts`
   (`getStartOfPeriod`, `getEndOfPeriod`, `navigatePeriod`) según el día de corte. Fechas "en voz alta" locales en
   `screens/deudas/fechas.ts` y `diaLargo` en MovimientosScreen.
6. **Confirmaciones dentro de la app** (`Hoja`, `ConfirmarDescarte`, `utils/confirmar.ts::confirmarAccion/confirmarBorrado`),
   no `window.confirm`. Borrar nunca al primer toque.
7. **Errores**: nunca `catch {}` que pinte vacío para datos principales; `useCarga` + `EstadoError`. Mensajes del
   servidor en español vía `utils/mensajes.ts`.
8. **Textos en español "como lo dice una persona"**, sin "(s)": "1 pago", "3 pagos".
9. Ancho máximo ~560 px y centrado en hojas y botones (se usa en web de escritorio también).

---

## 7. Pruebas

- Comando: `cd frontend && npm test` (= `jest`). Cobertura: `npx jest --coverage` (umbral global: statements 80,
  branches 65, functions 72, lines 82; excluye `theme/`, `types/index.ts`, `AppNavigator.tsx`, `test-utils.tsx`).
- Preset `jest-expo`, `setupFiles: jest.setup.js` (mock de AsyncStorage, `useToast` mockeado globalmente,
  silencia avisos de `act`/Animated), `testTimeout 20000`. Requiere `node_modules` instalado (`npm ci --legacy-peer-deps`).
- **~67 suites** (archivos `*.test.ts(x)`), todas dentro de `src/`: `src/__tests__/` (reglas transversales),
  `screens/__tests__/` (pantallas), `components/*/__tests__`, `contexts/__tests__`, `hooks/__tests__`,
  `services/__tests__`, `utils/__tests__`. Helper `src/test-utils.tsx`.

### Pruebas "de reglas" (fallan si rompes convenciones)

| Prueba | Qué exige |
|---|---|
| `src/__tests__/NadaSeEscribeMasPequeno.test.ts` | Ningún `fontSize` < 14 en screens/components/navigation; excepciones con motivo; escala ≥ 14 y creciente. |
| `src/__tests__/NadaInteractivoSinNombre.test.ts` | Todo `TouchableOpacity`/`Pressable` con `accessibilityLabel` o `accessible={false}`; y que la prueba encuentre elementos (no pasa en vacío). |
| `src/__tests__/ElContrasteSeMide.test.ts` | Contraste WCAG AA de los colores de ambos temas. |
| `src/__tests__/ArreglosQA.test.tsx` | ErrorBoundary por pantalla; **rutas legibles** (cada pantalla tiene su ruta, params inválidos, objetos fuera de la URL, `volverOInicio`); 401/403 cierran sesión como se describe; mensajes en español; contraseña segura. Si agregas una pantalla, agrégala a `RUTAS`. |
| `screens/__tests__/InicioYDona.test.tsx` | Ubicación y orden en la dona, `<1%`, "Más" con > 16, **lo pagado con ahorro va en la porción pero no en el gasto del centro**, centro → Presupuesto, hoja de categoría, recuadros, Deudas que fallan no tumban Inicio. |
| `screens/__tests__/LaAppAlEstiloMonefy.test.tsx` | Inicio (solo MIS pendientes), Por confirmar, "Por pagar" del recuadro = lista, liberar, Anotar (calculadora, ahorro, tarjeta a 12 cuotas, buscador, sin monto no anota), Presupuesto, Categoría, frase del cierre, **orden de Movimientos (más movido primero, vacías al final) y "Expandir todo"**, búsqueda sin tildes. |
| `screens/__tests__/ArreglosQaUx.test.tsx` | Anotar: "Más" no pierde nada; cerrar con algo escrito pregunta; calendario; teclado físico; contraste; cómo se pagó; Movimientos pagado/por pagar; Inicio sigue al selector. |
| `screens/__tests__/AhorrosYDeudas.test.tsx`, `ArreglosQAPantallas.test.tsx` | Ahorros, Deudas, `validarTarjeta`, menú, Presupuesto, Ingresos suma = encabezado, Plan legible, Por confirmar, Entrar, Hogar, categorías sin duplicados. |

No hay prueba de los **gestos** de Inicio ni de que la nota de Anotar siga visible por defecto (solo se busca por su
etiqueta): esas dos cosas hay que verificarlas a mano.

---

## 8. Cosas raras, duplicadas o muertas

1. **Nombres que confunden**: `screens/movements/` (formulario) vs `screens/movimientos/` (lista). No son duplicados.
2. **`CategoryDrillDown` (`screens/summary/`)** es resto de la pestaña "Resumen": solapa con `Movimientos?categoriaId=`,
   ignora el selector Hogar/persona, está tipado con un `SummaryStackParamList` inexistente y su `onConfirm` traga el
   error (`movementService.confirm(...).catch(() => null)`).
3. **La ruta raíz se llama `MainTabs`** aunque ya no hay pestañas; `@react-navigation/bottom-tabs` sigue en
   `package.json` sin uso. `SettingsScreen` también se tipa con un `SettingsStackParamList` viejo.
4. **Sub-gasto**: `MovementFormScreen` soporta `parentMovement` (y AppNavigator titula "Sub-gasto"), pero **ninguna
   pantalla navega con ese parámetro**: la función no es alcanzable desde la UI.
5. **`DonaDeCategorias` acepta `onAnterior/onSiguiente`** (deslizar para cambiar de periodo) pero Inicio no los pasa;
   además Inicio usa ese mismo gesto horizontal para Anotar gasto/ingreso.
6. **PlanScreen** no usa `AlcanceContext` ni `Encabezado`: su periodo/mes es propio y no sigue el selector.
   Usa emojis como íconos de estado (el resto de la app migró a `Icono` SVG).
7. **Tema**: `ThemeContext` permite claro, pero `NavigationContainer` tiene `dark: true` fijo y `StatusBar style="light"`
   fijo; `app.json` dice `userInterfaceStyle: "light"`, `name/slug: "frontend"`.
8. **`formatCompacto`** escribe `$` y `es-CO` a mano: no respeta la moneda configurada en CurrencyConfig.
9. **Onboarding** con textos de una versión vieja ("salud, hábitos", "gráficos de dona").
10. **Funciones de service sin uso**: `adminService.usuario/crearUsuario`, `preferenciasService.configuracion/guardarAjuste`,
    `householdService.addMember/setDisplayName`.
11. **`ExportImportScreen`** solo exporta (la importación se retiró) y llama `api` directo, sin service.
12. **Dos sistemas de fechas cortas**: `formatDateShort` (`dd/mm`) y `deudas/fechas.ts`/`diaLargo` ("6 nov"); y el verde de ahorro
    está definido dos veces: `VERDE_AHORRO` (Anotar, reutilizado en MovementForm) y `colorAhorro(isDark)` en AhorrosScreen (más oscuro en tema claro).
13. **Las pantallas "de siempre"** (Programados, Ajustes, Categorías, Admin, Plan, DrillDown, MovementForm) usan cabecera
    nativa, `Card`/`Button` y otro estilo visual que las nuevas (`Encabezado`, hojas inferiores).
14. `docker-compose.yml` de la raíz trae una IP de red local como valor por defecto de `API_URL`; `frontend/dist/` y
    `frontend/coverage/` están en disco (ignorados por git).
