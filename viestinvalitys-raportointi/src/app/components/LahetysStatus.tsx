'use client';
import { CheckCircle, Error, Warning, WatchLater } from '@mui/icons-material';
import { Status, LahetyksenVastaanottoTila } from '../lib/types';
import { getLahetyksenVastaanottajia, getLahetysStatus, getVastaanottajatPerStatus } from '../lib/util';
import { Box } from '@mui/material';
import { useTranslation } from '../i18n/clientLocalization';
import { ophColors } from '@opetushallitus/oph-design-system';

export const StatusIcon = ({ status }: { status: string }) => {
  switch (status) {
    case Status.EPAONNISTUI:
      return <Error sx={{ color: ophColors.alias.error }} />;
    case Status.KESKEN:
      return <WatchLater sx={{ color: ophColors.yellow1 }} />;
    case Status.ONNISTUI:
      return <CheckCircle sx={{ color: ophColors.alias.success }} />;
    default:
      return <Warning />;
  }
};

export const StatusTeksti = ({tilat, statusLocalized}: {
  tilat: LahetyksenVastaanottoTila[] | undefined,
  statusLocalized: string
}) => {
  const { t } = useTranslation();
  if (!tilat || tilat.length < 1) {
    return t('lahetys.tila.eiviestia')
  }
  const statusText = t('lahetys.tila.yhteenveto', {vastaanottajatPerStatus: getVastaanottajatPerStatus(tilat), vastaanottajatYht: getLahetyksenVastaanottajia(tilat),  status: statusLocalized});
  return statusText;
};

export const LahetysStatus = ({
  tilat,
}: {
  tilat: LahetyksenVastaanottoTila[];
}) => {
  const { t } = useTranslation();
  const status = getLahetysStatus(tilat.map((tila) => tila.vastaanottotila));
  return (
    <Box display="flex" alignItems="center">
      <Box marginRight={2} >
        <StatusIcon status={status} />
      </Box>
      <StatusTeksti tilat={tilat} statusLocalized={t(`tila.${status}`)}/>
    </Box>
  );
};
