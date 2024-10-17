'use client';

import { CircularProgress, CircularProgressProps } from '@mui/material';
import { useTranslations } from 'next-intl';

export const ClientSpinner = (props: CircularProgressProps) => {
  const t = useTranslations();
  return <CircularProgress aria-label={t('yleinen.ladataan')} {...props} />;
};
