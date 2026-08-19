import React from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import Svg, { Circle, Text as SvgText } from 'react-native-svg';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';
import { formatCurrency } from '../../utils/format';

interface DonutSlice {
  id: string;
  label: string;
  value: number;
  percentage: number;
  color: string;
}

interface CenterLine {
  label: string;
  value: number;
  color: string;
  bold?: boolean;
}

interface DonutChartProps {
  data: DonutSlice[];
  size?: number;
  strokeWidth?: number;
  centerLabel?: string;
  centerValue?: number;
  centerLines?: CenterLine[];
  onSlicePress?: (slice: DonutSlice) => void;
}

const DonutChart: React.FC<DonutChartProps> = ({
  data,
  size = 220,
  strokeWidth = 35,
  centerLabel,
  centerValue,
  centerLines,
  onSlicePress,
}) => {
  const { colors } = useTheme();
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const center = size / 2;

  let cumulativePercentage = 0;

  const slices = data.filter(d => d.percentage > 0).map(slice => {
    const dashLength = (slice.percentage / 100) * circumference;
    const gapLength = circumference - dashLength;
    const offset = circumference - (cumulativePercentage / 100) * circumference + circumference * 0.25;
    cumulativePercentage += slice.percentage;
    return { ...slice, dashLength, gapLength, offset };
  });

  const hasCenterLines = centerLines && centerLines.length > 0;
  const lineHeight = 18;
  const totalLinesHeight = hasCenterLines ? centerLines.length * lineHeight : 0;
  const startY = hasCenterLines ? center - totalLinesHeight / 2 + 6 : center;

  return (
    <View style={styles.container}>
      <Svg width={size} height={size}>
        {/* Background circle */}
        <Circle cx={center} cy={center} r={radius} stroke={colors.border} strokeWidth={strokeWidth} fill="none" opacity={0.3} />
        {/* Slices */}
        {slices.map((slice) => (
          <Circle
            key={slice.id}
            cx={center} cy={center} r={radius}
            stroke={slice.color}
            strokeWidth={strokeWidth}
            fill="none"
            strokeDasharray={`${slice.dashLength} ${slice.gapLength}`}
            strokeDashoffset={slice.offset}
            strokeLinecap="butt"
            onPress={() => onSlicePress?.(slice)}
          />
        ))}
        {/* Center: single value mode */}
        {!hasCenterLines && centerLabel && (
          <>
            <SvgText x={center} y={center - 8} textAnchor="middle" fill={colors.textSecondary} fontSize={13} fontWeight="500">
              {centerLabel}
            </SvgText>
            <SvgText x={center} y={center + 16} textAnchor="middle" fill={colors.text} fontSize={18} fontWeight="700">
              {formatCurrency(centerValue ?? 0)}
            </SvgText>
          </>
        )}
        {/* Center: multi-line mode (balance view) - solo valores con color */}
        {hasCenterLines && centerLines.map((line, i) => (
          <SvgText
            key={line.label}
            x={center}
            y={startY + i * lineHeight}
            textAnchor="middle"
            fill={line.color}
            fontSize={line.bold ? 16 : 14}
            fontWeight={line.bold ? '700' : '600'}
          >
            {formatCurrency(line.value)}
          </SvgText>
        ))}
      </Svg>

      {/* Legend */}
      <View style={styles.legend}>
        {/*
          * La dona en si es un dibujo y no hay forma de "oirla", pero su
          * LEYENDA si: cada entrada lleva el nombre y el importe juntos, que
          * es exactamente la informacion que da el grafico.
          */}
        {data.filter(d => d.percentage > 0).map(slice => (
          <TouchableOpacity
            key={slice.id}
            style={styles.legendItem}
            onPress={() => onSlicePress?.(slice)}
            activeOpacity={0.7}
            accessibilityRole="button"
            accessibilityLabel={`${slice.label}: ${slice.value}`}
            accessibilityHint="Ver el detalle de esta categoria"
          >
            <View style={[styles.legendDot, { backgroundColor: slice.color }]} />
            <View style={styles.legendText}>
              <AppText variant="caption" style={{ color: colors.text }}>{slice.label}</AppText>
              <AppText variant="caption" style={{ color: colors.textSecondary }}>
                {slice.percentage.toFixed(1)}% - {formatCurrency(slice.value)}
              </AppText>
            </View>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { alignItems: 'center' },
  legend: { marginTop: 16, width: '100%', paddingHorizontal: 8 },
  legendItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 6 },
  legendDot: { width: 12, height: 12, borderRadius: 6, marginRight: 10 },
  legendText: { flex: 1, flexDirection: 'row', justifyContent: 'space-between' },
});

export default DonutChart;
