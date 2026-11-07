import React from 'react';
import { View } from 'react-native';
import Icono from './Icono';
import { tintaSobre } from '../../utils/tinta';

interface Props {
  icono?: string;
  color?: string;
  tamano?: number;
}

/*
 * La categoria dibujada siempre igual en toda la app: su color de fondo y su
 * icono de linea en blanco. Antes la dona usaba estos iconos y Configuracion,
 * Programados, el Plan y las listas seguian con emojis, asi que la misma
 * categoria se veia de dos formas.
 */
const CirculoCategoria: React.FC<Props> = ({ icono, color = '#6C5CE7', tamano = 32 }) => (
  <View
    accessible={false}
    style={{ width: tamano, height: tamano, borderRadius: tamano / 2, backgroundColor: color,
      alignItems: 'center', justifyContent: 'center' }}
  >
    <Icono nombre={icono} color={tintaSobre(color)} tamano={Math.round(tamano * 0.55)} />
  </View>
);

export default CirculoCategoria;
