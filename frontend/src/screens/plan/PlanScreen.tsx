import React, { useState, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Spinner from '../../components/atoms/Spinner';
import EstadoError from '../../components/molecules/EstadoError';
import EmptyState from '../../components/molecules/EmptyState';
import CapsuleToggle from '../../components/molecules/CapsuleToggle';
import { spacing } from '../../theme';
import { planService, PlanItem, PlanWindow, Quincena, Responsabilidades } from '../../services/planService';
import { useCarga, exigir } from '../../hooks/useCarga';
import { formatCurrency, fechaLocalISO, getMonthName, fechaHumana, humanizarAviso, plural } from '../../utils/format';
import CirculoCategoria from '../../components/atoms/CirculoCategoria';
import { useFocusEffect } from '@react-navigation/native';

/**
 * EL PLAN: lo que pasa con la plata entre un pago y el siguiente.
 *
 * Responde, en este orden, lo que se pregunta al recibir un pago: cuanto
 * llego de verdad, que hay que pagar con eso, que hay que separar, cuanto se
 * ahorra y cuanto queda. Todo lo calcula el servidor desde los programados
 * (ver PlanFinancieroService): aqui no se suma nada a mano, para que esta
 * pantalla y la de Presupuesto no puedan contar historias distintas.
 *
 * "Disponible para gastar" NO es el saldo del banco: es el saldo menos lo que
 * todavia tiene que salir antes del siguiente pago. Confundir los dos es
 * justo el error que esta pantalla viene a evitar.
 */
type Vista = 'quincena' | 'calendario' | 'hogar' | 'proyeccion';

const ESTADOS: Record<string, { etiqueta: string; icono: string }> = {
  PAGADO: { etiqueta: 'Pagado', icono: '✅' },
  PENDIENTE: { etiqueta: 'Pendiente', icono: '⏳' },
  VENCIDO: { etiqueta: 'Vencido', icono: '⚠️' },
  PREVISTO: { etiqueta: 'Previsto', icono: '🗓️' },
  PROVISION: { etiqueta: 'Plata separada', icono: '🫙' },
  /* Se libero para usarlo en otra cosa: ya no hay que pagarlo este periodo. */
  LIBERADO: { etiqueta: 'Liberado (no se paga)', icono: '↩️' },
};

/* Un estado que esta pantalla no conozca no puede tumbarla: se muestra como
   previsto. (Asi se cayo el plan cuando el servidor empezo a mandar LIBERADO.) */
const estadoDe = (status: string) => ESTADOS[status] || ESTADOS.PREVISTO;

const PlanScreen: React.FC = () => {
  const { colors } = useTheme();
  const { user } = useAuth();
  const budgetDay = user?.budgetStartDay || 1;
  const [vista, setVista] = useState<Vista>('quincena');
  const [quincena, setQuincena] = useState<Quincena | null>(null);
  const [mes, setMes] = useState(() => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), 1); });
  const [calendario, setCalendario] = useState<PlanItem[]>([]);
  const [hogar, setHogar] = useState<Responsabilidades | null>(null);
  const [proyeccion, setProyeccion] = useState<PlanWindow[]>([]);

  const traer = useCallback(async () => {
    if (vista === 'quincena') {
      setQuincena(exigir(await planService.quincena()) || null);
    } else if (vista === 'calendario') {
      const desde = fechaLocalISO(mes);
      const hasta = fechaLocalISO(new Date(mes.getFullYear(), mes.getMonth() + 1, 0));
      setCalendario(exigir(await planService.calendar(desde, hasta))?.items || []);
    } else if (vista === 'hogar') {
      setHogar(exigir(await planService.responsibilities(budgetDay)) || null);
    } else {
      setProyeccion(exigir(await planService.projection(budgetDay, 3)) || []);
    }
  }, [vista, mes, budgetDay]);

  const { cargando, refrescando, error, cargar, refrescar } = useCarga(traer);
  useFocusEffect(useCallback(() => { cargar(); }, [cargar]));

  const color = (n: number) => (n < 0 ? colors.expense : colors.income);

  const Fila = ({ etiqueta, valor, fuerte, signo }: { etiqueta: string; valor: number; fuerte?: boolean; signo?: '-' | '+' }) => (
    <View style={styles.fila} accessible accessibilityLabel={`${etiqueta}: ${formatCurrency(valor)}`}>
      <AppText variant={fuerte ? 'body' : 'caption'} color={fuerte ? colors.text : colors.textSecondary}>{etiqueta}</AppText>
      <AppText variant={fuerte ? 'subtitle' : 'body'} color={fuerte ? color(valor) : colors.text}>
        {signo === '-' && valor > 0 ? '-' : ''}{formatCurrency(valor)}
      </AppText>
    </View>
  );

  const Item = ({ i, conPagador }: { i: PlanItem; conPagador?: boolean }) => (
    <View style={styles.item} accessible
      accessibilityLabel={`${i.name}, ${fechaHumana(i.date)}, ${formatCurrency(i.amount)}, ${estadoDe(i.status).etiqueta}`}>
      <View style={{ marginRight: 10 }}><CirculoCategoria icono={i.categoryIcon} color={i.categoryColor} tamano={30} /></View>
      <View style={{ flex: 1 }}>
        <AppText variant="body" numberOfLines={1}>{i.name}</AppText>
        <AppText variant="caption" color={colors.textSecondary}>
          {fechaHumana(i.date)} · {estadoDe(i.status).icono} {estadoDe(i.status).etiqueta}
          {conPagador && i.payerName ? ` · Paga ${i.payerName}` : ''}
          {i.notes ? ` · ${i.notes}` : ''}
        </AppText>
      </View>
      <AppText variant="body"
        color={i.status === 'LIBERADO' ? colors.textSecondary : i.income ? colors.income : i.status === 'VENCIDO' ? colors.expense : colors.text}
        style={i.status === 'LIBERADO' ? { textDecorationLine: 'line-through' } : undefined}>
        {i.income ? '+' : ''}{formatCurrency(i.amount)}
      </AppText>
    </View>
  );

  const Lista = ({ titulo, items }: { titulo: string; items?: PlanItem[] }) =>
    items && items.length > 0 ? (
      <View style={styles.lista}>
        <AppText variant="label" style={styles.subtitulo}>{titulo}</AppText>
        {items.map((i, n) => <Item key={`${i.scheduledMovementId}-${i.date}-${n}`} i={i} />)}
      </View>
    ) : null;

  const Ventana = ({ v, titulo, detalle }: { v: PlanWindow; titulo: string; detalle: boolean }) => (
    <Card style={styles.tarjeta}>
      <AppText variant="subtitle">{titulo}</AppText>
      <AppText variant="caption" color={colors.textSecondary} style={styles.rango}>
        Del {fechaHumana(v.start)} al {fechaHumana(v.end)}
      </AppText>
      <Fila etiqueta="Ingreso" valor={v.grossIncome} />
      {v.deductions > 0 && <Fila etiqueta="Descuentos de nómina" valor={v.deductions} signo="-" />}
      <Fila etiqueta="Ingreso efectivo" valor={v.netIncome} fuerte />
      <Fila etiqueta={`Por pagar (pagado ${formatCurrency(v.paymentsPaid)})`} valor={v.payments} signo="-" />
      {v.savings > 0 && <Fila etiqueta="Ahorro" valor={v.savings} signo="-" />}
      {v.provisions > 0 && <Fila etiqueta="Plata separada" valor={v.provisions} signo="-" />}
      <View style={[styles.separador, { borderColor: colors.border }]} />
      <Fila etiqueta={v.leftover < 0 ? 'No alcanza: faltan' : 'Queda (respaldo y gasto libre)'} valor={v.leftover} fuerte />
      {v.tight && (
        <AppText variant="caption" color={colors.expense} style={styles.aviso}>
          Esta quincena no alcanza. Mira qué plata separada se puede mover a la otra quincena sin incumplir un vencimiento.
        </AppText>
      )}
      {detalle && (
        <>
          <Lista titulo="Ingresos" items={v.incomeItems} />
          <Lista titulo="Descuentos de nómina (ya descontados del sueldo)" items={v.deductionItems} />
          <Lista titulo="Qué debo pagar" items={v.paymentItems} />
          <Lista titulo="Ahorro" items={v.savingItems} />
          <Lista titulo="Plata que debo separar" items={v.provisionItems} />
        </>
      )}
    </Card>
  );

  const renderQuincena = () => {
    if (!quincena) return null;
    if (!quincena.hasIncome) {
      return (
        <EmptyState
          icon="🗓️"
          title="Aún no hay ingresos programados a tu nombre"
          message="El plan se arma desde tus días de pago. Programa tu salario (y quién lo recibe) en Programados."
        />
      );
    }
    return (
      <>
        {quincena.alerts.length > 0 && (
          <Card style={[styles.tarjeta, { borderColor: colors.warning, borderWidth: 1 }]}>
            <AppText variant="label" style={styles.subtitulo}>Avisos</AppText>
            {quincena.alerts.map((a, n) => (
              <AppText key={n} variant="caption" style={styles.alerta}
                color={a.type === 'VENCIDO' || a.type === 'QUINCENA_AJUSTADA' ? colors.expense : colors.text}>
                {a.type === 'VENCIDO' ? 'Vencido · ' : a.type === 'PROXIMO' ? 'Próximo · ' : a.type === 'CUOTA_TERMINA' ? 'Termina · ' : ''}{humanizarAviso(a.message)}
                {a.amount ? ` (${formatCurrency(a.amount)})` : ''}
              </AppText>
            ))}
          </Card>
        )}

        <Ventana v={quincena.current} titulo="Esta quincena" detalle />
        {quincena.overdueFromBefore.length > 0 && (
          <Card style={styles.tarjeta}>
            <Lista titulo="Pendiente de antes" items={quincena.overdueFromBefore} />
          </Card>
        )}
        <Ventana v={quincena.next} titulo="Mi próxima quincena" detalle />

        {quincena.installments.length > 0 && (
          <Card style={styles.tarjeta}>
            <AppText variant="subtitle">Cuotas</AppText>
            {quincena.installments.map((c) => (
              <View key={c.scheduledMovementId} style={styles.item} accessible
                accessibilityLabel={`${c.name}: faltan ${c.remainingInstallments} de ${c.totalInstallments} cuotas, ${formatCurrency(c.remainingTotal)}`}>
                <View style={{ flex: 1 }}>
                  <AppText variant="body">{c.name}</AppText>
                  <AppText variant="caption" color={colors.textSecondary}>
                    {c.paidInstallments}/{c.totalInstallments} pagadas · faltan {c.remainingInstallments}
                    {c.nextDate ? ` · próxima ${fechaHumana(c.nextDate)}` : ''} · termina {fechaHumana(c.endDate)}
                  </AppText>
                  <AppText variant="caption" color={colors.income}>
                    Al terminar se liberan {formatCurrency(c.monthlyFreed)} al mes
                  </AppText>
                </View>
                <AppText variant="body">{formatCurrency(c.remainingTotal)}</AppText>
              </View>
            ))}
          </Card>
        )}
      </>
    );
  };

  const renderCalendario = () => {
    const porDia: Record<string, PlanItem[]> = {};
    calendario.forEach((i) => { (porDia[i.date] = porDia[i.date] || []).push(i); });
    const dias = Object.keys(porDia).sort();
    return (
      <>
        <View style={styles.navMes}>
          <TouchableOpacity onPress={() => setMes(new Date(mes.getFullYear(), mes.getMonth() - 1, 1))}
            accessibilityRole="button" accessibilityLabel="Mes anterior" style={styles.flecha}>
            <AppText variant="subtitle">‹</AppText>
          </TouchableOpacity>
          <AppText variant="subtitle">{getMonthName(mes.getMonth())} {mes.getFullYear()}</AppText>
          <TouchableOpacity onPress={() => setMes(new Date(mes.getFullYear(), mes.getMonth() + 1, 1))}
            accessibilityRole="button" accessibilityLabel="Mes siguiente" style={styles.flecha}>
            <AppText variant="subtitle">›</AppText>
          </TouchableOpacity>
        </View>
        {dias.length === 0 && <EmptyState icon="🗓️" title="Nada programado" message="No hay pagos, ingresos ni plata por separar este mes." />}
        {dias.map((dia) => (
          <Card key={dia} style={styles.tarjeta}>
            <AppText variant="label">{fechaHumana(dia)}</AppText>
            {porDia[dia].map((i, n) => <Item key={`${i.scheduledMovementId}-${i.date}-${n}`} i={i} conPagador />)}
          </Card>
        ))}
      </>
    );
  };

  const renderHogar = () => {
    if (!hogar) return null;
    return (
      <>
        <Card style={styles.tarjeta}>
          <AppText variant="subtitle">Quién paga qué</AppText>
          <AppText variant="caption" color={colors.textSecondary} style={styles.rango}>
            Del {fechaHumana(hogar.periodStart)} al {fechaHumana(hogar.periodEnd)} · lo común suma {formatCurrency(hogar.sharedTotal)}.
            Lo privado de cada uno no se cuenta aquí.
          </AppText>
        </Card>
        {hogar.people.map((p) => (
          <Card key={p.userId} style={styles.tarjeta}>
            <AppText variant="subtitle">{p.name || (p.isMe ? 'Yo' : `Persona ${p.userId}`)}{p.isMe ? ' (tú)' : ''}</AppText>
            <Fila etiqueta="Ingresos del periodo" valor={p.income} />
            <Fila etiqueta={`Paga de lo común (${p.sharePct}%)`} valor={p.sharedTotal} fuerte />
            <Fila etiqueta="Ya pagado" valor={p.sharedPaid} />
            {(p.sharedByKind?.SAVING || 0) > 0 && <Fila etiqueta="  de eso, ahorro" valor={p.sharedByKind.SAVING} />}
            {(p.sharedByKind?.PROVISION || 0) > 0 && <Fila etiqueta="  de eso, plata separada" valor={p.sharedByKind.PROVISION} />}
            {(p.sharedByKind?.DEDUCTION || 0) > 0 && <Fila etiqueta="  de eso, descuentos de nómina" valor={p.sharedByKind.DEDUCTION} />}
            <View style={styles.lista}>
              {(p.items || []).map((i, n) => <Item key={`${i.scheduledMovementId}-${i.date}-${n}`} i={i} />)}
            </View>
          </Card>
        ))}
      </>
    );
  };

  const renderProyeccion = () => (
    <>
      {proyeccion.map((v, n) => (
        <Card key={v.start} style={styles.tarjeta}>
          <AppText variant="subtitle">{n === 0 ? 'Este periodo' : `Dentro de ${plural(n, 'periodo')}`}</AppText>
          <AppText variant="caption" color={colors.textSecondary} style={styles.rango}>
            Del {fechaHumana(v.start)} al {fechaHumana(v.end)}
          </AppText>
          <Fila etiqueta="Ingreso efectivo" valor={v.netIncome} />
          <Fila etiqueta="Pagos" valor={v.payments} signo="-" />
          <Fila etiqueta="Ahorro" valor={v.savings} signo="-" />
          <Fila etiqueta="Plata separada" valor={v.provisions} signo="-" />
          <Fila etiqueta="Queda" valor={v.leftover} fuerte />
          {v.endingInstallments && v.endingInstallments.length > 0 && (
            <AppText variant="caption" color={colors.income} style={styles.aviso}>
              🎉 Terminan: {v.endingInstallments.join(', ')}. Desde el siguiente periodo se liberan {formatCurrency(v.freedNextPeriod || 0)} al mes.
            </AppText>
          )}
        </Card>
      ))}
    </>
  );

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.toggle}>
        <CapsuleToggle
          options={[
            { label: 'Quincena', value: 'quincena', color: colors.primary },
            { label: 'Calendario', value: 'calendario', color: colors.primary },
            { label: 'Hogar', value: 'hogar', color: colors.primary },
            { label: 'Futuro', value: 'proyeccion', color: colors.primary },
          ]}
          selected={vista}
          onChange={(v) => setVista(v as Vista)}
        />
      </View>
      {cargando ? <Spinner fullScreen /> : error ? <EstadoError mensaje={error} onReintentar={cargar} /> : (
        <ScrollView
          contentContainerStyle={styles.contenido}
          refreshControl={<RefreshControl refreshing={refrescando} onRefresh={refrescar} tintColor={colors.primary} />}
        >
          {vista === 'quincena' && renderQuincena()}
          {vista === 'calendario' && renderCalendario()}
          {vista === 'hogar' && renderHogar()}
          {vista === 'proyeccion' && renderProyeccion()}
        </ScrollView>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { padding: spacing.md, paddingBottom: spacing.sm },
  toggle: { paddingHorizontal: spacing.md, marginBottom: spacing.sm },
  contenido: { padding: spacing.md, paddingTop: 0, paddingBottom: spacing.xl * 2 },
  tarjeta: { marginBottom: spacing.md },
  rango: { marginBottom: spacing.sm },
  fila: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 3 },
  separador: { borderTopWidth: 1, marginVertical: spacing.xs },
  aviso: { marginTop: spacing.sm },
  alerta: { marginBottom: 4 },
  lista: { marginTop: spacing.sm },
  subtitulo: { marginBottom: spacing.xs },
  item: { flexDirection: 'row', alignItems: 'center', paddingVertical: 6, gap: spacing.sm },
  navMes: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: spacing.sm },
  flecha: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
});

export default PlanScreen;
