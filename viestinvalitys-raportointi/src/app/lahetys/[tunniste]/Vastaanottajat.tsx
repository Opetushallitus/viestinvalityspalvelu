'use client';
import {
  Box,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
import { Status, VastaanotonTila, Vastaanottaja } from '../../lib/types';
import { getLahetysStatus } from '../../lib/util';
import { StatusIcon } from '@/app/components/LahetysStatus';
import ViewViesti from './ViewViesti';
import { StyledCell, StyledHeaderCell, StyledTable, StyledTableBody } from '@/app/components/StyledTable';
import { Typography } from '@opetushallitus/oph-design-system';
import { useTranslation } from '@/app/i18n/clientLocalization';

const VastaanottajanStatus = ({
  tila,
}: {
  tila: VastaanotonTila;
}) => {
  const { t } = useTranslation();
  const status = getLahetysStatus([tila]);
  return (
    <Box display="flex" alignItems="center">
      <Box marginRight={2} >
        <StatusIcon status={status} />
      </Box>
      {t('vastaanottaja.tila', { status: t(status) })}
    </Box>
  );
};

const Toiminnot = ({ tila }: { tila: VastaanotonTila }) => {
  const { t } = useTranslation();
  if (getLahetysStatus([tila]) === Status.EPAONNISTUI) {
    return <Typography>{t('vastaanottaja.laheta-uudelleen')}</Typography>;
  }
  return <></>;
};

const VastaanottajatTable = ({
  vastaanottajat,
  onMassaviesti,
}: {
  vastaanottajat: Vastaanottaja[];
  onMassaviesti: boolean;
}) => {
  const { t } = useTranslation();
  return (
    <TableContainer sx={{ maxHeight: '440px' }}>
      <StyledTable stickyHeader aria-label="Vastaanottajat">
        <TableHead>
          <TableRow>
            <StyledHeaderCell>{t('vastaanottajat.nimi')}</StyledHeaderCell>
            <StyledHeaderCell>{t('vastaanottajat.sahkoposti')}</StyledHeaderCell>
            <StyledHeaderCell>{t('vastaanottajat.tila')}</StyledHeaderCell>
            <StyledHeaderCell>{t('vastaanottajat.toiminnot')}</StyledHeaderCell>
          </TableRow>
        </TableHead>
        <StyledTableBody>
          {vastaanottajat.map((row) => (
            <TableRow key={row.tunniste}>
              <StyledCell>{row.nimi}</StyledCell>
              <StyledCell>{row.sahkoposti}</StyledCell>
              <StyledCell>
                <VastaanottajanStatus tila={row.tila}/>
              </StyledCell>
              <StyledCell>
                <Toiminnot tila={row.tila} />
                {!onMassaviesti ? (
                  <ViewViesti viestiTunniste={row.viestiTunniste} />
                ) : (
                  <></>
                )}
              </StyledCell>
            </TableRow>
          ))}
        </StyledTableBody>
      </StyledTable>
    </TableContainer>
  );
};

export default VastaanottajatTable;
