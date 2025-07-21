'use client';
import { styled } from '@mui/material/styles';
import { Box, BoxProps } from '@mui/material';
import { ophColors } from '@opetushallitus/oph-design-system';
import { withDefaultProps } from './mui-utils';

export const DEFAULT_BOX_BORDER = `2px solid ${ophColors.grey100}`;

export const MainContainer = withDefaultProps(
    styled(Box)(({ theme }) => ({
      padding: theme.spacing(4),
      border: DEFAULT_BOX_BORDER,
      backgroundColor: ophColors.white,
    })),
    {
      component: 'main',
    } as BoxProps,
  ) as typeof Box;