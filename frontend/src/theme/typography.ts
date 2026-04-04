export const fontSize = {
  xs: 15,
  sm: 15,
  md: 18,
  lg: 22,
  xl: 26,
  xxl: 35,
  title: 44,
} as const;

export const fontWeight = {
  regular: '400' as const,
  medium: '500' as const,
  semiBold: '600' as const,
  bold: '700' as const,
  extraBold: '800' as const,
};

export type FontSize = typeof fontSize;
export type FontWeight = typeof fontWeight;
