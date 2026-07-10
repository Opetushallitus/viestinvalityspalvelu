import { createTheme, ThemeProvider } from '@mui/material/styles';
import { CssBaseline } from '@mui/material';
import { THEME_OVERRIDES } from '../theme';

export function OphThemeProvider({ children }: { children: React.ReactNode }) {
  const theme = createTheme(THEME_OVERRIDES);
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </ThemeProvider>
  );
}
