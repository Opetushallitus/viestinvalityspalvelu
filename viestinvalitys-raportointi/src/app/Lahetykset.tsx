'use client'
import { Link as MuiLink, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
// importoidaan MUI Link ja Nextjs Link komponentit eri nimillä
import NextLink from 'next/link';
import { Lahetys } from './lib/types';
import LocalDateTime from './components/LocalDateTime';
import { LahetysStatus} from './components/LahetysStatus';
import { useSearchParams } from 'next/navigation';

  const LahetyksetTable = ({lahetykset}: {lahetykset: Lahetys[]}) => {
    const searchParams = useSearchParams()
    return (
    <TableContainer sx={{ maxHeight: 440 }}>
    <Table
      stickyHeader
      aria-label="Lähetykset"
    >
      <TableHead>
        <TableRow>
          <TableCell>Luotu</TableCell>
          <TableCell>Lähettäjän nimi</TableCell>
          <TableCell>Lähettävä palvelu</TableCell>
          <TableCell>Otsikko</TableCell>
          <TableCell>Tilat</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {lahetykset
          .map((row) => (
            <TableRow key={row.lahetysTunniste}>
              <TableCell><LocalDateTime date={row.luotu} /></TableCell>
              <TableCell>{row.lahettajanNimi}</TableCell>
              <TableCell>{row.lahettavaPalvelu}</TableCell>
              <TableCell>
                <MuiLink component={NextLink} href={'/lahetys/' + row.lahetysTunniste + '?'+searchParams.toString()} prefetch={false}>
                  {row.otsikko}
                </MuiLink>
              </TableCell>
              <TableCell><LahetysStatus tilat={row.tilat || []} /></TableCell>
            </TableRow>
          ))}
      </TableBody>
    </Table>
  </TableContainer>
  )}

export default LahetyksetTable

