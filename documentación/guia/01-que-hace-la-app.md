# 01 · Qué hace la app (vista de quien la usa)

Este documento explica la app **como la vive el usuario**: qué ve, qué toca y
qué significa cada cifra. El detalle técnico (archivos, fórmulas, endpoints)
está en `02`, `03` y `04`. Antes de cambiar cualquier pantalla, entiende primero
qué espera ver quien la usa.

## Para quién es

Para **un hogar de dos personas**. Cada una tiene su usuario. Las dos
pertenecen al mismo **hogar**: comparten algunas categorías (mercado, servicios,
arriendo…) y tienen otras privadas. Al entrar, cada persona ve **lo suyo**.
Arriba hay un selector **Hogar / persona** para ver lo del hogar o lo de la
otra persona, y nadie ve lo privado del otro.

El periodo no es el mes calendario: va **de un día de corte al siguiente**. Se
configura en Ajustes, y suele ser el día en que llega el sueldo. Todas las
pantallas comparten el periodo elegido en la barra de arriba.

## Glosario: las palabras de la app

| Palabra en la app | Qué es | En el código |
|---|---|---|
| **Movimiento** | Un gasto o un ingreso anotado, con monto, fecha, categoría, **nota** y quién pagó | `Movement` |
| **Nota** | El texto libre de "¿en qué fue?". **Es lo más importante para los usuarios**: con ella saben en qué se fue la plata | `description` |
| **Categoría** | Mercado, Carro, Salario… Es de gasto o de ingreso, personal o del hogar, y puede tener subcategorías | `Category` (`EXPENSE`/`INCOME`) |
| **Pendiente / Por pagar / Por confirmar** | Un movimiento que todavía no se paga (lo generó un programado o se anotó como "por pagar") | `confirmed = false` |
| **Programado** | Algo que se repite: salario, arriendo, servicios, cuota de una deuda, ahorro mensual. Genera pendientes | `ScheduledMovement` |
| **Plata separada / Provisión** | Plata que se aparta para una categoría (p. ej. "mercado del mes"). No genera pendientes, se va gastando | programado de clase `PROVISION` |
| **Presupuesto** | Cuánto se planea gastar por categoría en el periodo | `BudgetAllocation` |
| **Ahorro** | Una categoría donde se mete plata (programado de clase `SAVING`). Tiene **saldo** = lo metido − lo pagado con él | no es un tipo de categoría: se deduce de sus programados |
| **Pagado con ahorros** | Un gasto que se pagó sacando plata de un ahorro (p. ej. una cuota del carro) | `fundedFromCategoryId` |
| **Tarjeta** | Una tarjeta de crédito con su día de corte. Las compras se anotan con "Pagado con: tarjeta" y pueden ser a cuotas | `Card`, `card_id` |
| **Pagar tarjeta** | Abonar a la tarjeta, con el monto (mayor que 0 y hasta lo que se debe a esa fecha) y la fecha que se quiera. **No es un gasto** | `CardPayment` |
| **Deuda** | Algo que se paga en cuotas (un préstamo). Se ve cuántas cuotas van y cuántas faltan; no lleva intereses | programado con fin |
| **Libre para gastar** | Lo que de verdad queda del periodo después de lo gastado, lo que falta pagar, lo que se va a ahorrar y la plata separada que falta | `freeToSpend` |
| **Quincena / mes de salario** | La misma idea, pero entre un pago de sueldo y el siguiente, y solo de la persona | `PlanFinancieroService.ventana` |
| **Liberar** | Decidir que algo programado (p. ej. el ahorro de este mes) no se va a hacer: esa plata pasa a lo libre | `released_amount` |
| **Cierre** | Cómo quedó un periodo terminado: qué se cumplió, qué se pasó y qué se liberó | `CierreScreen` |

## Las pantallas

### Inicio: lo primero que se ve (estilo de la app Monefy)

- **Una dona con las categorías que tienen gasto** alrededor, cada una con su
  ícono, su línea y su porcentaje.
- **Dejar presionado el centro** abre la lista de todas las categorías (también
  las sin gasto y los ahorros) para anotar en cualquiera.
- **En el centro:** los ingresos en verde y lo gastado en rojo. Lo pagado con
  ahorros **no** entra en el rojo: aparece aparte como "+ X con ahorros", pero
  sí se ve en la porción de su categoría.
- **Tocar una categoría** abre una hoja con "Anotar aquí", "Ver sus
  movimientos" y "Ver su presupuesto".
- **Cuatro recuadros:** Ingresos, Por pagar, Ahorros y Deudas, cada uno lleva
  a su pantalla.
- **Abajo**, "Libre para gastar". Al tocarlo se abren los movimientos.
- **Dos botones**: anotar un gasto o un ingreso.
- **Gestos**: deslizar a la derecha anota un gasto, a la izquierda un ingreso,
  y deslizar hacia arriba desde la parte de abajo abre Movimientos. En el
  navegador del celular se desactivan los gestos propios del navegador, porque
  si no "derecha" sería "página anterior".

### Anotar (teclado rápido)

Monto, categoría, fecha, **nota siempre visible**, "Pagado con" (efectivo, un
ahorro o una tarjeta, y con tarjeta también el número de cuotas) y quién pagó.
El botón "Más" abre el formulario completo.

### Movimientos

Lista de todo lo anotado en el periodo, con dos vistas:
- **Por categoría**: categorías ordenadas de más a menos gastado, que se
  abren una por una o todas con "Expandir todo".
- **Por fecha**: un encabezado por día.

Muestra Gastos, Ingresos o **Todos**. En Todos se ven las entradas y las
salidas, y aparte lo pagado con ahorros. En cada fila **la nota va primero** y
debajo la categoría, con qué se pagó y quién. Se puede buscar por nota o por
categoría.

### Presupuesto, Categoría y Cierre

Lo planeado contra lo real por categoría, con su estado (dentro, en riesgo,
excedido). Permite copiar el presupuesto del periodo anterior. El cierre resume
cada periodo terminado.

### Por confirmar

Los pendientes del periodo y los atrasados. Se confirman uno por uno o
varios a la vez, y se puede corregir el monto real antes de confirmar.

### Ingresos

Lo que se esperaba recibir frente a lo que llegó, incluidos los ingresos sueltos.

### Ahorros

Saldo de cada ahorro, con acciones para meter plata o pagar algo con él.

### Deudas

- **Tarjetas:** lo que se debe hoy, el abono a favor, el último pago,
  "Anotar compra" y "Pagar tarjeta".
- **Deudas en cuotas:** cuántas cuotas van, cuántas faltan y cuánto falta, con
  opción de adelantar una cuota.

### Plan y quincenas

Para cada persona:
- **la quincena o el mes de salario actual y el siguiente:** ingreso, pagos
  programados, ahorro, otros gastos ya hechos, plata separada que falta y lo
  que queda libre;
- alertas y una proyección.

### Programados

Crear y editar lo que se repite. Las deudas también se cambian aquí.

### Ajustes

- día de corte;
- moneda;
- hogar (miembros);
- categorías;
- exportar a Excel;
- administración (solo admin).

## Cosas que el usuario espera y que NO se deben romper

- Que la nota aparezca **siempre**: al anotar y en las listas.
- Que "Libre para gastar" en Inicio y "Queda libre" en el Plan cuenten la
  misma historia.
- Que una compra con tarjeta cuente **una sola vez**: el día en que se compra.
- Que lo pagado con ahorros no haga parecer que se gastó más sueldo del real.
- Que el orden de las categorías sea por lo gastado y, a igual gasto,
  alfabético.
- Que todo se pueda editar: el monto real a veces no coincide con el planeado.
- Que la app siga **simple**.
