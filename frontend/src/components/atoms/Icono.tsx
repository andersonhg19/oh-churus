import React from 'react';
import Svg, { Path, Circle, Rect } from 'react-native-svg';
import { Text } from 'react-native';
import { getIconEmoji } from '../../utils/iconMap';

/*
 * Iconos de linea dibujados en SVG, todos con el mismo trazo. Reemplazan a los
 * emojis en la barra, los botones y la dona, porque un emoji cambia de dibujo
 * segun el celular y no se puede pintar con el color del tema. Las claves son
 * las mismas que ya guardan las categorias (iconMap). Si llega una que no
 * esta, se usa el emoji de siempre para no dejar un hueco.
 */
type Dibujo = (c: string) => React.ReactNode;

const P = (d: string): Dibujo => (c) => (
  <Path d={d} stroke={c} strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
);
const varios = (...ds: Dibujo[]): Dibujo => (c) => (
  <>{ds.map((d, i) => <React.Fragment key={i}>{d(c)}</React.Fragment>)}</>
);
const circulo = (cx: number, cy: number, r: number): Dibujo => (c) => (
  <Circle cx={cx} cy={cy} r={r} stroke={c} strokeWidth={2} fill="none" />
);
const rect = (x: number, y: number, w: number, h: number, rx = 2): Dibujo => (c) => (
  <Rect x={x} y={y} width={w} height={h} rx={rx} stroke={c} strokeWidth={2} fill="none" />
);

const DIBUJOS: Record<string, Dibujo> = {
  // navegacion y acciones
  menu: P('M4 6h16M4 12h16M4 18h16'),
  back: P('M15 5l-7 7 7 7'),
  left: P('M15 6l-6 6 6 6'),
  right: P('M9 6l6 6-6 6'),
  down: P('M6 9l6 6 6-6'),
  plus: P('M12 5v14M5 12h14'),
  minus: P('M5 12h14'),
  check: P('M5 12l5 5 9-10'),
  close: P('M6 6l12 12M18 6L6 18'),
  search: varios(circulo(11, 11, 6), P('M20 20l-4-4')),
  list: P('M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01'),
  edit: P('M4 20h4L19 9l-4-4L4 16zM14 6l4 4'),
  trash: P('M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13'),
  history: varios(P('M3 12a9 9 0 1 0 3-6.7L3 8'), P('M3 3v5h5M12 7v5l3 2')),
  people: varios(circulo(9, 8, 3.5), P('M2.5 20c.8-3.5 3.5-5.5 6.5-5.5s5.7 2 6.5 5.5M16 4.6a3.5 3.5 0 0 1 0 6.8')),
  settings: varios(circulo(12, 12, 3), P('M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1')),
  logout: P('M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10'),
  target: varios(circulo(12, 12, 9), circulo(12, 12, 5), circulo(12, 12, 1)),
  upload: P('M12 16V4M7 9l5-5 5 5M4 20h16'),
  // categorias (claves de iconMap)
  home: varios(P('M4 11l8-6 8 6v9H4z'), P('M10 20v-5h4v5')),
  key: varios(circulo(8, 15, 4), P('M11 12l9-9M17 6l3 3')),
  zap: P('M13 2L4 14h7l-1 8 9-12h-7z'),
  droplet: P('M12 3s6 7 6 11a6 6 0 0 1-12 0c0-4 6-11 6-11z'),
  flame: P('M12 3c1 4 6 6 6 11a6 6 0 0 1-12 0c0-3 2-4 2-7 2 1 3 3 3 5 1-2 1-6 1-9z'),
  tool: P('M14 6a4 4 0 0 0 5 5l-9 9-3-3 9-9a4 4 0 0 0-2-2z'),
  bulb: P('M9 18h6M10 21h4M12 3a6 6 0 0 0-4 10.5c.7.7 1 1.5 1 2.5h6c0-1 .3-1.8 1-2.5A6 6 0 0 0 12 3z'),
  bed: P('M3 18V8M3 14h18v4M21 14v-2a3 3 0 0 0-3-3h-7v5'),
  wallet: varios(rect(3, 6, 18, 13), P('M16 13h2M3 9h18')),
  money: varios(rect(3, 6, 18, 12), circulo(12, 12, 2.5)),
  dollar: P('M12 3v18M16 7c-1-1-2.5-1.5-4-1.5-2 0-4 1-4 3s2 2.5 4 3 4 1 4 3-2 3-4 3c-1.5 0-3-.5-4-1.5'),
  coin: varios(circulo(12, 12, 8), P('M12 8v8')),
  bank: P('M3 10l9-6 9 6M5 10v8M9 10v8M15 10v8M19 10v8M3 20h18'),
  'credit-card': varios(rect(3, 6, 18, 12), P('M3 10h18M7 15h4')),
  'piggy-bank': P('M5 11a7 6 0 0 1 13-2h2v4h-2a7 6 0 0 1-3 3v3h-3v-2h-2v2H7v-3a6 6 0 0 1-2-5z'),
  chart: P('M4 20V10M10 20V4M16 20v-7M2 20h20'),
  'bar-chart': P('M4 20V10M10 20V4M16 20v-7M2 20h20'),
  percent: varios(P('M19 5L5 19'), circulo(7, 7, 2), circulo(17, 17, 2)),
  'shopping-cart': varios(P('M3 4h2l2 11h11l2-8H6'), circulo(9, 19, 1.4), circulo(17, 19, 1.4)),
  'shopping-bag': varios(P('M5 8h14l-1 12H6z'), P('M9 8V6a3 3 0 0 1 6 0v2')),
  coffee: varios(P('M4 9h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z'), P('M16 10h2a2 2 0 0 1 0 4h-2M8 3v3M12 3v3')),
  pizza: varios(P('M12 21L3 6c6-3 12-3 18 0z'), circulo(11, 10, 1)),
  apple: P('M12 7c-3-2-8-1-8 5 0 4 3 9 5 9 1 0 2-1 3-1s2 1 3 1c2 0 5-5 5-9 0-6-5-7-8-5zM12 7c0-2 1-4 3-4'),
  cooking: P('M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M16 3c-2 2-2 6 0 8v10'),
  cake: varios(rect(4, 11, 16, 9), P('M4 15c2 1 4 1 6 0s4-1 6 0 4 1 4 0M12 4v4')),
  wine: P('M8 3h8l-1 6a3 3 0 0 1-6 0zM12 12v8M8 20h8'),
  car: varios(P('M5 16v-4l2-5h10l2 5v4'), P('M4 16h16v3H4z')),
  bus: varios(rect(5, 3, 14, 15), P('M5 11h14M8 18v2M16 18v2')),
  truck: varios(P('M3 6h11v10H3zM14 10h4l3 3v3h-7'), circulo(7, 18, 1.5), circulo(17, 18, 1.5)),
  bike: varios(circulo(6, 16, 3.5), circulo(18, 16, 3.5), P('M6 16l4-8h5l3 8M10 8l2 8')),
  plane: P('M3 13l18-8-6 16-3-7z'),
  fuel: varios(rect(4, 4, 10, 16), P('M14 9h3l2 3v6a1 1 0 0 1-2 0v-3h-3M7 8h4')),
  navigation: P('M12 2l7 19-7-4-7 4z'),
  train: varios(rect(6, 3, 12, 14, 3), P('M8 21l2-4M16 21l-2-4M6 11h12')),
  briefcase: varios(rect(3, 7, 18, 13), P('M9 7V4h6v3M3 13h18')),
  laptop: varios(rect(5, 5, 14, 10), P('M3 19h18')),
  desktop: varios(rect(3, 4, 18, 12), P('M9 20h6M12 16v4')),
  tv: varios(rect(4, 5, 16, 11), P('M9 20h6M12 16v4')),
  phone: varios(rect(7, 3, 10, 18, 2), P('M11 18h2')),
  mail: varios(rect(3, 5, 18, 14), P('M3 7l9 6 9-6')),
  calendar: varios(rect(3, 5, 18, 16), P('M3 10h18M8 3v4M16 3v4')),
  clock: varios(circulo(12, 12, 9), P('M12 7v5l3 2')),
  'trending-up': P('M3 17l6-6 4 4 8-8M15 7h6v6'),
  heart: P('M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z'),
  hospital: varios(rect(4, 4, 16, 16), P('M12 8v8M8 12h8')),
  pill: varios(P('M10 4.5a4.9 4.9 0 0 1 7 7l-6.5 6.5a4.9 4.9 0 0 1-7-7z'), P('M7 10l7 7')),
  thermometer: P('M14 14V5a2 2 0 0 0-4 0v9a4 4 0 1 0 4 0z'),
  activity: P('M3 12h4l3-8 4 16 3-8h4'),
  book: P('M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM4 19V5'),
  graduation: P('M2 9l10-5 10 5-10 5zM6 11v5c3 2 9 2 12 0v-5'),
  award: varios(circulo(12, 9, 5), P('M9 13l-2 8 5-3 5 3-2-8')),
  bookmark: P('M6 3h12v18l-6-4-6 4z'),
  film: varios(rect(3, 4, 18, 16), P('M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4')),
  music: varios(P('M9 18V5l10-2v13'), circulo(6, 18, 3), circulo(16, 16, 3)),
  gamepad: varios(rect(2, 7, 20, 10, 5), P('M7 12h4M9 10v4M15 11h.01M18 13h.01')),
  headphones: P('M4 15v-3a8 8 0 0 1 16 0v3M4 15h3v5H4zM17 15h3v5h-3z'),
  party: P('M4 20l5-14 9 9zM14 4l1 2M19 9l2-1M17 3l-1 3'),
  sports: varios(circulo(12, 12, 9), P('M12 3v18M3 12h18')),
  gym: P('M6 7v10M18 7v10M3 10v4M21 10v4M6 12h12'),
  gift: varios(rect(4, 9, 16, 11, 1), P('M4 13h16M12 9v11M12 9c-2-4-6-3-5 0M12 9c2-4 6-3 5 0')),
  pet: varios(circulo(7, 9, 1.6), circulo(12, 6.5, 1.6), circulo(17, 9, 1.6), P('M8 17c0-3 2-5 4-5s4 2 4 5c0 2-8 2-8 0z')),
  baby: varios(circulo(12, 8, 4), P('M6 21c1-4 3-6 6-6s5 2 6 6')),
  clothes: P('M8 3l-5 3 2 4 2-1v12h10V9l2 1 2-4-5-3c0 2-2 3-4 3S8 5 8 3z'),
  scissors: varios(circulo(6, 6, 3), circulo(6, 18, 3), P('M8 8l12 10M8 16L20 6')),
  star: P('M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z'),
  shield: P('M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z'),
  squirrel: P('M15 20H7a4 4 0 0 1 0-8c1 0 2 .5 2 .5S8 8 12 6c0-2 2-3 3-3 0 1 1 2 1 3 3 1 4 4 4 7 0 4-2 7-5 7z'),
  user: varios(circulo(12, 8, 4), P('M4 21c1-4 4-6 8-6s7 2 8 6')),
  users: varios(circulo(9, 8, 3.5), P('M2.5 20c.8-3.5 3.5-5.5 6.5-5.5s5.7 2 6.5 5.5M16 4.6a3.5 3.5 0 0 1 0 6.8')),
  nails: P('M9 21V11a3 3 0 0 1 6 0v10M9 15h6'),
  receipt: P('M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6'),
  tune: P('M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M14 4v4M8 10v4M16 16v4'),
  'swap-horizontal': P('M4 8h14l-4-4M20 16H6l4 4'),
  'help-circle-outline': varios(circulo(12, 12, 9), P('M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.7.3-1 .9-1 1.7M12 17h.01')),
};

export const tieneDibujo = (nombre?: string) => !!nombre && !!DIBUJOS[nombre];

interface Props {
  nombre?: string;
  color: string;
  tamano?: number;
}

const Icono: React.FC<Props> = ({ nombre, color, tamano = 22 }) => {
  const dibujo = nombre ? DIBUJOS[nombre] : undefined;
  if (!dibujo) {
    return (
      <Text style={{ fontSize: Math.max(14, Math.round(tamano * 0.8)) }} accessible={false}>
        {getIconEmoji(nombre)}
      </Text>
    );
  }
  return (
    <Svg width={tamano} height={tamano} viewBox="0 0 24 24" accessible={false}>
      {dibujo(color)}
    </Svg>
  );
};

export default Icono;
