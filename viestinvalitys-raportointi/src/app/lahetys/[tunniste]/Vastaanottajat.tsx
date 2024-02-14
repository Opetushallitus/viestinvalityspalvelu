'use client'
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import { VastaanotonTila, Vastaanottaja } from "../../lib/types";
import { getLahetysStatus } from "../../lib/util";
import { Box, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from "@mui/material";
import { StatusIcon } from "@/app/components/LahetysStatus";

  const lahetyksenStatus = (tila: VastaanotonTila): string => {
    const status = 'Lähetys ' + getLahetysStatus([tila])
    return status
  }

  const toiminnot = (tila: VastaanotonTila): string => {
    if(getLahetysStatus([tila])==='epäonnistui') {
      return 'Lähetä uudelleen'
    }
    return ''
  }

const VastaanottajatTable = ({vastaanottajat}: {vastaanottajat: Vastaanottaja[]}) => {
  return (
  <TableContainer sx={{ maxHeight: 440 }}>
  <Table
    stickyHeader
    aria-label="Vastaanottajat"
  >
    <TableHead>
      <TableRow>
        <TableCell>Nimi</TableCell>
        <TableCell>Sähköposti</TableCell>
        <TableCell>Tila</TableCell>
        <TableCell>Toiminnot</TableCell>
      </TableRow>
    </TableHead>
    <TableBody>
      {vastaanottajat
        .map((row) => (
          <TableRow key={row.tunniste}>
            <TableCell>{row.nimi}</TableCell>
            <TableCell>{row.sahkoposti}</TableCell>
            <TableCell><Box display="flex" alignItems="center"><StatusIcon status={lahetyksenStatus(row.tila)} />&nbsp; {lahetyksenStatus(row.tila)}</Box></TableCell>
            <TableCell>{toiminnot(row.tila)}</TableCell>
          </TableRow>
        ))}
    </TableBody>
  </Table>
</TableContainer>
)}

const Vastaanottajat = ({vastaanottajat}: {vastaanottajat: Vastaanottaja[]}) => {

  return (
      vastaanottajat.length > 0 
      ?  <VastaanottajatTable vastaanottajat={vastaanottajat} /> 
      : <div>
          <FolderOutlinedIcon fontSize='large'/>
          <p>Vastaanottajia ei löytynyt</p>
        </div>
)}

export default Vastaanottajat
