'use client';
import { Box, CircularProgress } from '@mui/material';
import { useTranslations } from 'next-intl';

const Loading = () => {
  const t = useTranslations();
  return (
    <Box sx={{ display: 'flex' }}>
      <CircularProgress aria-label={t('yleinen.ladataan')}/>
    </Box>
  );
};

export default Loading;
