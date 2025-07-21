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
import { OphTypography, OphButton } from '@opetushallitus/oph-design-system';
import { useTranslations } from 'next-intl';

const VastaanottajanStatus = ({
  tila,
}: {
  tila: VastaanotonTila;
}) => {
  const t = useTranslations();
  const status = getLahetysStatus([tila]);
  return (
    <Box display="flex" alignItems="center">
      <Box marginRight={2} >
        <StatusIcon status={status} />
      </Box>
      {t('vastaanottaja.tila', { status: t(`tila.${status}`) })}
    </Box>
  );
};

const Toiminnot = ({ tila }: { tila: VastaanotonTila }) => {
  const t = useTranslations();
  if (getLahetysStatus([tila]) === Status.EPAONNISTUI) {
    return <OphTypography>{t('vastaanottaja.laheta-uudelleen')}</OphTypography>;
  }
  return <></>;
};

interface DownloadViestiProps {
  viestiTunniste?: string;
}

function DownloadViesti({ viestiTunniste }: DownloadViestiProps) {
  return (
    <form action={'/raportointi/v1/download/viesti'} method={'GET'}>
      <input hidden={true} name={'viestiTunniste'} value={viestiTunniste} />
      <OphButton type={'submit'}>Lataa viesti</OphButton>
    </form>
  );
}

const VastaanottajatTable = ({
  vastaanottajat,
  onMassaviesti,
  downloadEnabled,
}: {
  vastaanottajat: Vastaanottaja[];
  onMassaviesti: boolean;
  downloadEnabled?: boolean;
}) => {
  const t = useTranslations();
  return (
    <TableContainer sx={{ maxHeight: '440px' }}>
      <StyledTable stickyHeader aria-label={t('vastaanottajat.otsikko')}>
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
                {downloadEnabled ? (
                  <DownloadViesti viestiTunniste={row.viestiTunniste} />
                ) : (
                  <></>
                )}
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
