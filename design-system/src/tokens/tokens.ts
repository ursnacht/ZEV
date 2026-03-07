/**
 * ZEV Design System Tokens
 * Type-safe design tokens for consistent styling across the application
 */

export const colors = {
  // Primary palette
  primary: {
    base: '#4CAF50',
    hover: '#45a049',
    light: '#81C784',
    dark: '#388E3C',
  },

  // Secondary palette
  secondary: {
    base: '#2196F3',
    hover: '#0b7dda',
    active: '#3498db',
    light: '#64B5F6',
    dark: '#1976D2',
  },

  // Semantic colors
  danger: {
    base: '#f44336',
    hover: '#da190b',
    light: '#EF5350',
    dark: '#C62828',
  },

  success: {
    background: '#d4edda',
    text: '#155724',
    border: '#c3e6cb',
  },

  error: {
    background: '#f8d7da',
    text: '#721c24',
    border: '#f5c6cb',
  },

  // Neutral colors
  neutral: {
    white: '#ffffff',
    gray50: '#f8f8f8',
    gray100: '#f5f5f5',
    gray200: '#f9f9f9',
    gray300: '#e0e0e0',
    gray400: '#ddd',
    gray500: '#ccc',
    gray600: '#666',
    gray700: '#555',
    gray800: '#333',
    gray900: '#2c3e50',
  },
} as const;

export const spacing = {
  xxs: '5px',
  xs: '8px',
  sm: '10px',
  md: '15px',
  lg: '20px',
  xl: '30px',
  xxl: '40px',

  // Specific sizes
  spacing12: '12px',
  spacing16: '16px',
  spacing18: '18px',

  // Layout sizes
  maxHeight: '300px',
  chartHeight: '400px',
  chartMinHeight: '200px',
  chartMaxHeight: '1000px',
} as const;

export const borderRadius = {
  sm: '4px',
  md: '8px',
} as const;

export const typography = {
  fontFamily: {
    primary: 'Arial, sans-serif',
  },
  fontSize: {
    xs: '12px',
    sm: '13px',
    base: '14px',
    lg: '1.2em',
    xl: '24px',
  },
  fontWeight: {
    normal: 400,
    semibold: 600,
    bold: 700,
  },
} as const;

export const shadows = {
  card: '0 2px 4px rgba(0, 0, 0, 0.1)',
} as const;

export const transitions = {
  fast: '0.3s',
} as const;

export const layout = {
  maxWidth: '1200px',
  formMaxWidth: '600px',
  calculationMaxWidth: '800px',
} as const;

export const darkColors = {
  primary: {
    base: '#69db7c',
    hover: '#51cf66',
    light: '#8ce99a',
    dark: '#2f9e44',
  },
  secondary: {
    base: '#74c0fc',
    hover: '#4dabf7',
    active: '#339af0',
    light: '#a5d8ff',
    dark: '#1c7ed6',
  },
  danger: {
    base: '#ff6b6b',
    hover: '#fa5252',
    light: '#ffa8a8',
    dark: '#e03131',
  },
  success: {
    background: '#1c3829',
    text: '#69db7c',
    border: '#2f9e44',
  },
  error: {
    background: '#3b1219',
    text: '#ff6b6b',
    border: '#c92a2a',
  },
  neutral: {
    white: '#1e1e2e',
    gray50: '#181825',
    gray100: '#1e1e2e',
    gray200: '#27273a',
    gray300: '#313244',
    gray400: '#45475a',
    gray500: '#585b70',
    gray600: '#a6adc8',
    gray700: '#bac2de',
    gray800: '#cdd6f4',
    gray900: '#cdd6f4',
  },
} as const;

// Type exports for consumers
export type ColorToken = typeof colors;
export type DarkColorToken = typeof darkColors;
export type SpacingToken = typeof spacing;
export type TypographyToken = typeof typography;
export type BorderRadiusToken = typeof borderRadius;
export type ShadowToken = typeof shadows;
export type TransitionToken = typeof transitions;
export type LayoutToken = typeof layout;
