'use client'
import { DataGrid, GridColDef } from "@mui/x-data-grid";
import { VastaanotonTila, Vastaanottaja } from "../../lib/types";
import { getLahetysStatus } from "../../lib/util";

const columns: GridColDef[] = [
    { field: 'nimi', headerName: 'Nimi', width: 200 },
    { field: 'sahkoposti', headerName: 'Sähköposti', width: 300 },
    { field: 'tila',  valueGetter: ({ value }) => value && lahetyksenStatus(value), headerName: 'Tila', width: 200 },
    //{ field: 'tila', valueGetter: ({ value }) => value && toiminnot(value), headerName: 'Toiminnot', width: 150 },
  ];

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

  function getRowId(row: Vastaanottaja) {
    return row.tunniste
  }

  type Props = {
    vastaanottajat: Vastaanottaja[]
  }
const VastaanottajatGrid = ({vastaanottajat}: Props) => {
  return (
    <div style={{ height: 300, width: '100%' }}>
      <DataGrid rows={vastaanottajat} columns={columns} getRowId={getRowId}/>
    </div>
)}

export default VastaanottajatGrid
