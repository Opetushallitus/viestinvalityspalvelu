import { Box, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from "@mui/material";
import { VastaanotonTila, Vastaanottaja } from "../../lib/types";
import { getLahetysStatus } from "../../lib/util";
import { StatusIcon } from "@/app/components/LahetysStatus";
import ViestiModal from "./ViestiModal";


  const lahetyksenStatus = (tila: VastaanotonTila): string => {
    const status = 'Lähetys ' + getLahetysStatus([tila])
    return status
  }

  const Toiminnot = ({ tila }: { tila: VastaanotonTila }) => {
    if(getLahetysStatus([tila])==='epäonnistui') {
      return <Typography>Lähetä uudelleen</Typography>
    }
    return <></>
  }

const VastaanottajatTable = ({vastaanottajat, onMassaviesti}: {vastaanottajat: Vastaanottaja[], onMassaviesti: boolean}) => {
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
            <TableCell>
              <Toiminnot tila={row.tila}/>
              {!onMassaviesti ? <ViestiModal viestiTunniste={row.viestiTunniste}/> : <></>}
            </TableCell>
          </TableRow>
        ))}
    </TableBody>
  </Table>
</TableContainer>
)}

export default VastaanottajatTable
