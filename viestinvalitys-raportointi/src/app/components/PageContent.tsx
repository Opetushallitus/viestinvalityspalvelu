'use client';
import { styled } from '@mui/material/styles';
import { Box } from '@mui/material';

export const PageContent = styled(Box)(({ theme }) => ({
  paddingLeft: theme.spacing(4),
  paddingRight: theme.spacing(4),
  maxWidth: '1920px',
  margin: 'auto',
  width: '100%',
}));
