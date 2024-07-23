import {
  Box,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { VastaanotonTila, Vastaanottaja } from '../../lib/types';
import { getLahetysStatus } from '../../lib/util';
import { StatusIcon } from '@/app/components/LahetysStatus';
import ViewViesti from './ViewViesti';
import { StyledCell, StyledHeaderCell, StyledTable, StyledTableBody } from '@/app/components/StyledTable';

const lahetyksenStatus = (tila: VastaanotonTila): string => {
  const status = 'Lähetys ' + getLahetysStatus([tila]);
  return status;
};

const Toiminnot = ({ tila }: { tila: VastaanotonTila }) => {
  if (getLahetysStatus([tila]) === 'epäonnistui') {
    return <Typography>Lähetä uudelleen</Typography>;
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
  return (
    <TableContainer sx={{ maxHeight: 440 }}>
      <StyledTable stickyHeader aria-label="Vastaanottajat">
        <TableHead>
          <TableRow>
            <StyledHeaderCell>Nimi</StyledHeaderCell>
            <StyledHeaderCell>Sähköposti</StyledHeaderCell>
            <StyledHeaderCell>Tila</StyledHeaderCell>
            <StyledHeaderCell>Toiminnot</StyledHeaderCell>
          </TableRow>
        </TableHead>
        <StyledTableBody>
          {vastaanottajat.map((row) => (
            <TableRow key={row.tunniste}>
              <StyledCell>{row.nimi}</StyledCell>
              <StyledCell>{row.sahkoposti}</StyledCell>
              <StyledCell>
                <Box display="flex" alignItems="center">
                  <StatusIcon status={getLahetysStatus([row.tila])} />
                  &nbsp; {lahetyksenStatus(row.tila)}
                </Box>
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
