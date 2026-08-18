import React, { useState, useCallback, useEffect } from 'react';
import { View, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../../contexts/ThemeContext';
import { useToast } from '../../contexts/ToastContext';
import { useAuth } from '../../contexts/AuthContext';
import AppText from '../../components/atoms/Text';
import Card from '../../components/atoms/Card';
import Input from '../../components/atoms/Input';
import Button from '../../components/atoms/Button';
import { spacing } from '../../theme';
import { importService, ImportMapping } from '../../services/importService';
import { categoryService } from '../../services/categoryService';
import { Category, ImportPreview, ImportRow, ImportRowChoice } from '../../types';
import { formatCurrency } from '../../utils/format';
import { useAccionUnica } from '../../hooks/useAccionUnica';

/**
 * Importar el extracto del banco.
 *
 * ES LO QUE DECIDE SI LA APP SE USA O SE ABANDONA. Nadie deja una app de
 * finanzas porque los informes sean feos; todo el mundo la deja por teclear
 * sesenta movimientos al mes.
 *
 * DOS PASOS, Y EL PRIMERO NO ESCRIBE NADA. Primero se ve que va a pasar con
 * las sesenta filas; solo despues se escribe, y solo lo aceptado. Un
 * importador que escribe primero y deja arreglar el desastre despues es peor
 * que no tener importador.
 *
 * TRES LISTAS, NO DOS: nuevos, duplicados, y los que CONFIRMAN un pendiente
 * que ya esperaba una recurrencia. La tercera evita el peor resultado posible
 * —importar el arriendo como nuevo dejando el pendiente colgando y el gasto
 * contado dos veces— y es la que nadie piensa en poner.
 */
const ImportScreen: React.FC = () => {
  const { colors } = useTheme();
  const { showToast } = useToast();
  const { user } = useAuth();

  const [csv, setCsv] = useState('');
  const [banco, setBanco] = useState('');
  const [colFecha, setColFecha] = useState('0');
  const [colDescripcion, setColDescripcion] = useState('1');
  const [colImporte, setColImporte] = useState('2');
  const [conCabecera, setConCabecera] = useState(true);
  const [invertirSigno, setInvertirSigno] = useState(false);

  const [previa, setPrevia] = useState<ImportPreview | null>(null);
  const [categorias, setCategorias] = useState<Category[]>([]);
  const [elegidas, setElegidas] = useState<Record<number, string>>({});

  useEffect(() => {
    const cargar = async () => {
      if (!user) return;
      try {
        const res = await categoryService.getAll({ userId: user.userId, page: 0, size: 200 });
        if (res.correct && res.object) setCategorias(res.object.list || []);
      } catch {
        setCategorias([]);
      }
    };
    cargar();
  }, [user]);

  const mapeo = (): ImportMapping => ({
    bankName: banco.trim() || undefined,
    dateColumn: Number(colFecha),
    descriptionColumn: colDescripcion.trim() === '' ? undefined : Number(colDescripcion),
    amountColumn: Number(colImporte),
    hasHeader: conCabecera,
    invertSign: invertirSigno,
    rememberProfile: banco.trim().length > 0,
  });

  const verPrevia = useCallback(async () => {
    if (!csv.trim()) {
      showToast('warning', 'Validacion', 'Pega el contenido del extracto');
      return;
    }
    try {
      const res = await importService.preview(csv, mapeo());
      if (res.correct && res.object) {
        setPrevia(res.object);
        /* Se precargan las categorias que el diccionario sugiere: si no,
           habria que volver a clasificar a mano lo mismo de siempre, que es
           justo lo que el importador viene a evitar. */
        const sugeridas: Record<number, string> = {};
        res.object.newRows.forEach((f) => {
          if (f.suggestedCategoryId) sugeridas[f.row] = String(f.suggestedCategoryId);
        });
        setElegidas(sugeridas);
      } else {
        /* El backend explica que corregir ("revisa las columnas y el formato
           de fecha"), y ese texto vale mas que un "error" generico. */
        showToast('error', 'No se pudo leer', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo leer el archivo');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [csv, banco, colFecha, colDescripcion, colImporte, conCabecera, invertirSigno, showToast]);

  const { ejecutando: leyendo, ejecutar: ejecutarPrevia } = useAccionUnica(verPrevia);

  const importar = useCallback(async () => {
    if (!previa) return;

    const filas: ImportRowChoice[] = [
      ...previa.newRows
        .filter((f) => elegidas[f.row])
        .map((f) => ({ row: f.row, categoryId: elegidas[f.row] })),
      ...previa.confirmPending.map((f) => ({
        row: f.row,
        confirmsMovementId: f.matchedMovementId,
      })),
    ];

    if (filas.length === 0) {
      showToast('warning', 'Validacion', 'Elige la categoria de al menos una fila');
      return;
    }

    try {
      const res = await importService.confirm(csv, mapeo(), filas);
      if (res.correct && res.object) {
        showToast('success', 'Listo', res.object.message);
        setPrevia(null);
        setCsv('');
        setElegidas({});
      } else {
        showToast('error', 'Error', res.message);
      }
    } catch (err: any) {
      showToast('error', 'Error', err.message || 'No se pudo importar');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [previa, elegidas, csv, showToast]);

  const { ejecutando: importando, ejecutar: ejecutarImportar } = useAccionUnica(importar);

  const pintarFila = (fila: ImportRow, conCategoria: boolean) => (
    <Card key={fila.row} style={styles.fila}>
      <View style={styles.filaCabecera}>
        <AppText variant="body">{fila.description || '(sin descripcion)'}</AppText>
        <AppText
          variant="body"
          color={fila.amount < 0 ? colors.expense : colors.income}
          testID={`importe-${fila.row}`}
        >
          {formatCurrency(Math.abs(fila.amount))}
        </AppText>
      </View>
      <AppText variant="caption" color={colors.textSecondary}>
        {fila.date} · {fila.reason}
      </AppText>

      {conCategoria && (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filaCategorias}>
          {categorias.map((c) => {
            const elegida = elegidas[fila.row] === String(c.id);
            return (
              <TouchableOpacity
                key={c.id}
                testID={`cat-${fila.row}-${c.id}`}
                onPress={() =>
                  setElegidas((antes) => ({ ...antes, [fila.row]: String(c.id) }))
                }
                style={[
                  styles.chip,
                  {
                    backgroundColor: elegida ? colors.primary : colors.surface,
                    borderColor: elegida ? colors.primary : colors.border,
                  },
                ]}
              >
                <AppText variant="caption" color={elegida ? '#FFFFFF' : colors.text}>
                  {c.name}
                </AppText>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      )}
    </Card>
  );

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      <AppText variant="subtitle" style={styles.titulo}>Importar del banco 🐿️</AppText>

      {!previa && (
        <>
          <AppText variant="caption" color={colors.textSecondary} style={styles.ayuda}>
            Pega aqui el CSV que descargaste del banco. Antes de guardar nada vas a ver
            exactamente que se va a crear, que ya tenias, y que confirma un pago que la app
            estaba esperando.
          </AppText>

          <Input
            label="Contenido del archivo"
            value={csv}
            onChangeText={setCsv}
            placeholder="fecha,concepto,valor..."
            multiline
          />

          <Input
            label="Banco (para recordar las columnas la proxima vez)"
            value={banco}
            onChangeText={setBanco}
            placeholder="Bancolombia, Nequi..."
          />

          <View style={styles.columnas}>
            <View style={styles.columna}>
              <Input label="Col. fecha" value={colFecha} onChangeText={setColFecha}
                     placeholder="0" keyboardType="numeric" />
            </View>
            <View style={styles.columna}>
              <Input label="Col. concepto" value={colDescripcion} onChangeText={setColDescripcion}
                     placeholder="1" keyboardType="numeric" />
            </View>
            <View style={styles.columna}>
              <Input label="Col. valor" value={colImporte} onChangeText={setColImporte}
                     placeholder="2" keyboardType="numeric" />
            </View>
          </View>
          <AppText variant="caption" color={colors.textMuted} style={styles.ayuda}>
            Las columnas se cuentan desde 0: la primera es la 0.
          </AppText>

          <TouchableOpacity
            testID="alternar-cabecera"
            onPress={() => setConCabecera((a) => !a)}
            style={[styles.interruptor, {
              backgroundColor: conCabecera ? colors.secondary : colors.surface,
              borderColor: conCabecera ? colors.secondary : colors.border,
            }]}
          >
            <AppText variant="caption" color={conCabecera ? '#FFFFFF' : colors.text}>
              {conCabecera ? '✓ ' : ''}La primera fila son los titulos
            </AppText>
          </TouchableOpacity>

          <TouchableOpacity
            testID="alternar-signo"
            onPress={() => setInvertirSigno((a) => !a)}
            style={[styles.interruptor, {
              backgroundColor: invertirSigno ? colors.secondary : colors.surface,
              borderColor: invertirSigno ? colors.secondary : colors.border,
            }]}
          >
            <AppText variant="caption" color={invertirSigno ? '#FFFFFF' : colors.text}>
              {invertirSigno ? '✓ ' : ''}Mi banco exporta los gastos en positivo
            </AppText>
          </TouchableOpacity>

          <Button title="Ver que va a pasar" onPress={ejecutarPrevia} loading={leyendo} />
        </>
      )}

      {previa && (
        <>
          <Card style={styles.resumen}>
            <AppText variant="body" testID="resumen-previa">
              {previa.newRows.length} nuevos · {previa.duplicates.length} ya los tenias
              {previa.confirmPending.length > 0
                ? ` · ${previa.confirmPending.length} confirman un pendiente`
                : ''}
            </AppText>
            <AppText variant="caption" color={colors.textMuted}>
              Todavia no se ha guardado nada.
            </AppText>
          </Card>

          {previa.confirmPending.length > 0 && (
            <>
              <AppText variant="label" style={styles.seccion}>
                Confirman un pago que estabas esperando
              </AppText>
              <AppText variant="caption" color={colors.textSecondary} style={styles.ayuda}>
                No se crean de nuevo: se marca como pagado el pendiente que ya existia.
              </AppText>
              {previa.confirmPending.map((f) => pintarFila(f, false))}
            </>
          )}

          {previa.newRows.length > 0 && (
            <>
              <AppText variant="label" style={styles.seccion}>Nuevos</AppText>
              <AppText variant="caption" color={colors.textSecondary} style={styles.ayuda}>
                Elige la categoria de cada uno. Los que dejes sin categoria no se importan.
              </AppText>
              {previa.newRows.map((f) => pintarFila(f, true))}
            </>
          )}

          {previa.duplicates.length > 0 && (
            <>
              <AppText variant="label" style={styles.seccion}>Ya los tenias</AppText>
              <AppText variant="caption" color={colors.textSecondary} style={styles.ayuda}>
                Estos se quedan fuera para que no te aparezca el mes duplicado.
              </AppText>
              {previa.duplicates.map((f) => pintarFila(f, false))}
            </>
          )}

          <Button title="Importar" onPress={ejecutarImportar} loading={importando} />
          <Button title="Empezar de nuevo" onPress={() => setPrevia(null)} variant="secondary" />
        </>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: spacing.md, paddingBottom: spacing.xl * 2 },
  titulo: { marginBottom: spacing.md },
  ayuda: { marginBottom: spacing.md },
  columnas: { flexDirection: 'row', gap: spacing.sm },
  columna: { flex: 1 },
  interruptor: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: 8,
    borderWidth: 1,
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  resumen: { marginBottom: spacing.md },
  seccion: { marginTop: spacing.md, marginBottom: spacing.xs },
  fila: { marginBottom: spacing.sm },
  filaCabecera: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  filaCategorias: { marginTop: spacing.sm },
  chip: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    borderRadius: 16,
    borderWidth: 1,
    marginRight: spacing.xs,
  },
});

export default ImportScreen;
