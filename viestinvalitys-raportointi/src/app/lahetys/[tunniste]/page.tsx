import { Suspense } from 'react'
import { fetchLahetyksenVastaanottajat, fetchLahetys } from "../../lib/data";
import { Grid, Skeleton } from '@mui/material';
import { Lahetys } from '@/app/lib/types';
import LocalDateTime from '@/app/components/LocalDateTime';
import { lahetyksenStatus } from '@/app/lib/util';
import VastaanottajatTable from './Vastaanottajat';
import { LahetysStatus } from '@/app/components/LahetysStatus';
   
  export default async function Page({ params }: { params: { tunniste: string } }) {
    const lahetys: Lahetys = await fetchLahetys(params.tunniste)
    const data = await fetchLahetyksenVastaanottajat(params.tunniste)
    return (
      <div>
        <h1>Lähetysraportti</h1>
        <Grid container spacing={2} padding={12}>
          <Grid item xs={12}> <h2>{lahetys.otsikko}</h2></Grid>
          <Grid item xs={3}><b>Lähetyksen ajankohta</b></Grid>
          <Grid item xs={9}><LocalDateTime date={lahetys.luotu} /></Grid>
          <Grid item xs={3}><b>Lähettäjä</b></Grid>
          <Grid item xs={9}>{lahetys.lahettajanSahkoposti}</Grid>
          <Grid item xs={3}><b>Lähettäjän nimi, OID</b></Grid>
          <Grid item xs={9}>{lahetys.lahettajanNimi || '-'}, {lahetys.lahettavanVirkailijanOID || '-'}</Grid>          
          <Grid item xs={3}><b>Vastausosoite</b></Grid>
          <Grid item xs={9}>{lahetys.replyTo}</Grid>         
          <Grid item xs={3}><b>Palvelu</b></Grid>
          <Grid item xs={9}>{lahetys.lahettavaPalvelu}</Grid>
          <Grid item xs={3}><b>Hakuehdot</b></Grid>
          <Grid item xs={9}>TODO onko tämä kohta kälisuunnitelmassa lähetyksen metadata???</Grid>
          <Grid item xs={3}><b>Lähetystunnus</b></Grid>
          <Grid item xs={9}>{lahetys.lahetysTunniste}</Grid>
          <Grid item xs={3}><b>Lähetyksen tila</b></Grid>
          <Grid item xs={9} display="flex" alignItems="center"><LahetysStatus tilat={lahetys.tilat || []}/></Grid>
          <Grid item xs={12}>
            <h2>Vastaanottajat</h2>
            <Suspense fallback={<Skeleton variant="rectangular" width={210} height={60} />}>
              <VastaanottajatTable vastaanottajat={data.vastaanottajat}/>
            </Suspense>
          </Grid>
        </Grid>
      </div>
    )

  }
