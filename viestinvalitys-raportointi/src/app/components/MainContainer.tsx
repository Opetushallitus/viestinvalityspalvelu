'use client';
import { styled } from '@mui/material/styles';
import { Box } from '@mui/material';
import { DEFAULT_BOX_BORDER, colors, withDefaultProps } from '@/app/theme';

export const MainContainer = withDefaultProps(
    styled(Box)(({ theme }) => ({
      padding: theme.spacing(4),
      border: DEFAULT_BOX_BORDER,
      backgroundColor: colors.white,
    })),
    {
      component: 'main',
    },
  );