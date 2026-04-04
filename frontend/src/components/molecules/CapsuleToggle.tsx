import React, { useRef, useEffect } from 'react';
import { View, TouchableOpacity, Animated, StyleSheet } from 'react-native';
import AppText from '../atoms/Text';
import { useTheme } from '../../contexts/ThemeContext';

interface Option {
  label: string;
  value: string;
  color: string;
}

interface CapsuleToggleProps {
  options: Option[];
  selected: string;
  onChange: (value: string) => void;
}

const CapsuleToggle: React.FC<CapsuleToggleProps> = ({ options, selected, onChange }) => {
  const { colors } = useTheme();
  const selectedIndex = options.findIndex(o => o.value === selected);
  const slideAnim = useRef(new Animated.Value(selectedIndex)).current;

  useEffect(() => {
    if (options.length >= 2) {
      Animated.spring(slideAnim, {
        toValue: selectedIndex >= 0 ? selectedIndex : 0,
        useNativeDriver: false,
        tension: 100,
        friction: 12,
      }).start();
    }
  }, [selectedIndex, options.length]);

  const selectedOption = options[selectedIndex] || options[0];

  return (
    <View style={styles.container}>
      <Animated.View
        style={[
          styles.slider,
          {
            backgroundColor: selectedOption.color,
            width: `${100 / options.length}%` as any,
            left: options.length >= 2
              ? slideAnim.interpolate({
                  inputRange: options.map((_, i) => i),
                  outputRange: options.map((_, i) => `${(100 / options.length) * i}%`),
                })
              : '0%',
          },
        ]}
      />
      {options.map(option => (
        <TouchableOpacity
          key={option.value}
          style={styles.option}
          onPress={() => onChange(option.value)}
          activeOpacity={0.7}
        >
          <AppText
            variant="label"
            style={[
              styles.label,
              { color: selected === option.value ? '#FFFFFF' : colors.textMuted },
            ]}
          >
            {option.label}
          </AppText>
        </TouchableOpacity>
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    backgroundColor: 'rgba(128,128,128,0.15)',
    borderRadius: 16,
    padding: 3,
    position: 'relative',
    overflow: 'hidden',
  },
  slider: {
    position: 'absolute',
    top: 3,
    bottom: 3,
    borderRadius: 14,
  },
  option: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
    zIndex: 1,
  },
  label: {
    fontWeight: '600',
    fontSize: 15,
  },
});

export default CapsuleToggle;
