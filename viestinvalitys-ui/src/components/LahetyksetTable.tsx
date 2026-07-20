import { Link as MuiLink, TableContainer, TableHead, TableRow } from '@mui/material';
import { Link } from 'react-router-dom';
import { Lahetys } from '../lib/types';
import LocalDateTime from './LocalDateTime';
import { LahetysStatus } from './LahetysStatus';
import { StyledCell, StyledHeaderCell, StyledTable, StyledTableBody } from './StyledTable';
import { useTranslation } from 'react-i18next';

const LahetyksetTable = ({ lahetykset }: { lahetykset: Lahetys[] }) => {
  const { t } = useTranslation();
  return (
    <TableContainer>
      <StyledTable stickyHeader aria-label={t('lahetykset.label')}>
        <TableHead>
          <TableRow>
            <StyledHeaderCell>{t('lahetykset.luotu')}</StyledHeaderCell>
            <StyledHeaderCell>{t('lahetykset.lahettaja')}</StyledHeaderCell>
            <StyledHeaderCell>{t('lahetykset.palvelu')}</StyledHeaderCell>
            <StyledHeaderCell>{t('lahetykset.otsikko')}</StyledHeaderCell>
            <StyledHeaderCell>{t('lahetykset.tilat')}</StyledHeaderCell>
          </TableRow>
        </TableHead>
        <StyledTableBody>
          {lahetykset.map((row) => (
            <TableRow key={row.lahetysTunniste}>
              <StyledCell>
                <LocalDateTime date={row.luotu} />
              </StyledCell>
              <StyledCell>{row.lahettajanNimi}</StyledCell>
              <StyledCell>{row.lahettavaPalvelu}</StyledCell>
              <StyledCell>
                <MuiLink component={Link} to={`/lahetys/${row.lahetysTunniste}`}>
                  {row.otsikko}
                </MuiLink>
              </StyledCell>
              <StyledCell>
                <LahetysStatus tilat={row.tilat ?? []} />
              </StyledCell>
            </TableRow>
          ))}
        </StyledTableBody>
      </StyledTable>
    </TableContainer>
  );
};

export default LahetyksetTable;
