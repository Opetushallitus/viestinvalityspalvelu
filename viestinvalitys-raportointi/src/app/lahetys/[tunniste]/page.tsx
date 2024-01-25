import { Suspense } from 'react'
import { fetchLahetyksenVastaanottajat, fetchLahetys } from "../../lib/data";
import VastaanottajatGrid from './VastaanottajatGrid';
import { Grid, Skeleton } from '@mui/material';
import { Lahetys } from '@/app/lib/types';
import LocalDateTime from '@/app/LocalDateTime';
import LahetysStatus from '@/app/LahetysStatus';
import { lahetyksenStatus } from '@/app/lib/util';
   
  export default async function Page({ params }: { params: { tunniste: string } }) {
    console.log(params.tunniste)
    const lahetys: Lahetys = await fetchLahetys(params.tunniste)
    const vastaanottajat = await fetchLahetyksenVastaanottajat(params.tunniste)
    return (
      <div>
        <h1>Lähetysraportti</h1>
        <Grid container spacing={2} padding={12}>
          <Grid xs={12}><h2>{lahetys.otsikko}</h2></Grid>
          <Grid xs={3}><b>Lähetyksen ajankohta</b></Grid>
          <Grid xs={9}><LocalDateTime date={lahetys.luotu} /> - TODO vastaanottotilan ajankohta</Grid>
          <Grid xs={3}><b>Lähettäjä</b></Grid>
          <Grid xs={9}>{lahetys.lahettajanSahkoposti}</Grid>
          <Grid xs={3}><b>Lähettäjän nimi, OID</b></Grid>
          <Grid xs={9}>{lahetys.lahettajanNimi || '-'}, {lahetys.lahettavanVirkailijanOID || '-'}</Grid>          
          <Grid xs={3}><b>Vastausosoite</b></Grid>
          <Grid xs={9}>{lahetys.replyTo}</Grid>         
          <Grid xs={3}><b>Palvelu</b></Grid>
          <Grid xs={9}>{lahetys.lahettavaPalvelu}</Grid>
          <Grid xs={3}><b>Hakuehdot</b></Grid>
          <Grid xs={9}>TODO onko tämä metadata???</Grid>
          <Grid xs={3}><b>Lähetystunnus</b></Grid>
          <Grid xs={9}>{lahetys.lahetysTunniste}</Grid>
          <Grid xs={3}><b>Lähetyksen tila</b></Grid>
          <Grid xs={9}><LahetysStatus tilat={lahetys.tilat || []}/>{lahetyksenStatus(lahetys.tilat)}</Grid>
          <Grid xs={12}>
            <Suspense fallback={<Skeleton variant="rectangular" width={210} height={60} />}>
              <VastaanottajatGrid vastaanottajat={vastaanottajat.vastaanottajat}/>       
            </Suspense>
          </Grid>
        </Grid>
      </div>
    )

  }
