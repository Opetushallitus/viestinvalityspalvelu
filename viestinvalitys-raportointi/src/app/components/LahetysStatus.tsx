'use client';
import { CheckCircle, Error, Warning, WatchLater } from '@mui/icons-material';
import { Status, LahetyksenVastaanottoTila } from '../lib/types';
import { getLahetysStatus, lahetyksenStatus } from '../lib/util';
import { Box } from '@mui/material';
import { aliasColors, colors } from '../theme';

export const StatusIcon = ({ status }: { status: string }) => {
  switch (status) {
    case Status.EPAONNISTUI:
      return <Error sx={{ color: aliasColors.error }} />;
    case Status.KESKEN:
      return <WatchLater sx={{ color: colors.yellow1 }} />;
    case Status.ONNISTUI:
      return <CheckCircle sx={{ color: aliasColors.success }} />;
    default:
      return <Warning />;
  }
};

export const LahetysStatus = ({
  tilat,
}: {
  tilat: LahetyksenVastaanottoTila[];
}) => {
  const status = getLahetysStatus(tilat.map((tila) => tila.vastaanottotila));
  return (
    <Box display="flex" alignItems="center">
      <Box marginRight={2} >
        <StatusIcon status={status} />
      </Box>
      {lahetyksenStatus(tilat)}
    </Box>
  );
};
