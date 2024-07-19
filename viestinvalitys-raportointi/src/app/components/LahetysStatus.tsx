'use client';
import { CheckCircle, Error, Warning, WatchLater } from '@mui/icons-material';
import { Status, LahetyksenVastaanottoTila } from '../lib/types';
import { getLahetysStatus, lahetyksenStatus } from '../lib/util';
import { Box } from '@mui/material';
import { colors } from '../theme';

export const StatusIcon = ({ status }: { status: string }) => {
  if (status === Status.EPAONNISTUI) {
    return <Error color='error'  />;
  }
  if (status === Status.KESKEN) {
    return <WatchLater sx={{ color: colors.yellow1 }} />;
  }
  if (status === Status.ONNISTUI) {
    return <CheckCircle sx={{ color: colors.green2 }} />;
  }
  return <Warning />;
};

export const LahetysStatus = ({
  tilat,
}: {
  tilat: LahetyksenVastaanottoTila[];
}) => {
  const status = getLahetysStatus(tilat.map((tila) => tila.vastaanottotila));
  return (
    <Box display="flex" alignItems="center">
      {' '}
      <StatusIcon status={status} />
      &nbsp;{lahetyksenStatus(tilat)}
    </Box>
  );
};
