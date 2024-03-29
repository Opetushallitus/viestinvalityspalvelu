'use client'
import { Link as MuiLink, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
// importoidaan MUI Link ja Nextjs Link komponentit eri nimillä
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import NextLink from 'next/link';
import { Lahetys } from './lib/types';
import LocalDateTime from './components/LocalDateTime';
import { LahetysStatus} from './components/LahetysStatus';
import { ReadonlyURLSearchParams, useSearchParams } from 'next/navigation';

  const LahetyksetTable = ({lahetykset, searchParams}: {lahetykset: Lahetys[], searchParams: ReadonlyURLSearchParams}) => {
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
              <TableCell>
                <MuiLink component={NextLink} href={'/lahetys/' + row.lahetysTunniste + '?'+searchParams.toString()}>
                  {row.otsikko}
                </MuiLink>
              </TableCell>
              <TableCell>{row.lahettavaPalvelu}</TableCell>
              <TableCell><LahetysStatus tilat={row.tilat || []} /></TableCell>
            </TableRow>
          ))}
      </TableBody>
    </Table>
  </TableContainer>
  )}

  const Lahetykset = ({lahetykset}: {lahetykset: Lahetys[]}) => {
    const searchParams = useSearchParams()
    return (
      <Paper sx={{ width: '100%', overflow: 'hidden' }}>
        {lahetykset.length > 0 
        ?  <LahetyksetTable lahetykset={lahetykset} searchParams={searchParams}/> 
        : <div>
          <FolderOutlinedIcon fontSize='large'/>
          <p>Hakuehdoilla ei löytynyt tuloksia</p>
          </div>}
        
      </Paper>
  )}

export default Lahetykset

