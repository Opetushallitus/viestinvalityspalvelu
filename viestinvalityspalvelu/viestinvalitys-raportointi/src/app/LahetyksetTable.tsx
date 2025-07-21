'use client';
import {
  Link as MuiLink,
  TableContainer,
  TableHead,
  TableRow,
} from '@mui/material';
// importoidaan MUI Link ja Nextjs Link komponentit eri nimillä
import NextLink from 'next/link';
import { Lahetys } from './lib/types';
import LocalDateTime from './components/LocalDateTime';
import { LahetysStatus } from './components/LahetysStatus';
import { StyledCell, StyledHeaderCell, StyledTable, StyledTableBody } from './components/StyledTable';
import { useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';

const LahetyksetTable = ({ lahetykset }: { lahetykset: Lahetys[] }) => {
  const searchParams = useSearchParams();
  const t  = useTranslations();
  return (
    <TableContainer sx={{ maxHeight: '440px' }}>
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
                <MuiLink
                  component={NextLink}
                  href={{
                    pathname: `/lahetys/${row.lahetysTunniste}`,
                    query: searchParams.toString()
                  }}
                  prefetch={false}
                >
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
