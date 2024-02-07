import { Suspense } from 'react'
import { fetchLahetyksenVastaanottajat, fetchLahetys } from "../../lib/data";
import { Grid, Skeleton } from '@mui/material';
import { Lahetys } from '@/app/lib/types';
import LocalDateTime from '@/app/components/LocalDateTime';
import Vastaanottajat from './Vastaanottajat';
import { LahetysStatus } from '@/app/components/LahetysStatus';
import VastaanottajaHaku from './VastaanottajaHaku';
import VastaanottajatSivutus from './VastaanottajatSivutus';
import VirheAlert from '@/app/components/VirheAlert';
   
  export default async function Page({ params, searchParams }: { params: { tunniste: string }, 
    searchParams?: {    
      alkaen?: string
      sivutustila?: string
      hakukentta?: string
      hakusana?: string
      tila?: string
    } }) {
    const lahetys: Lahetys = await fetchLahetys(params.tunniste)
    const data = await fetchLahetyksenVastaanottajat(params.tunniste, 
      {alkaen: searchParams?.alkaen, sivutustila: searchParams?.sivutustila})
    const virheet = data?.virhe
    return (
      <div>
        <h1>Lähetysraportti</h1>
        <Grid container spacing={2} padding={2}>
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
          <Grid item xs={3}><b>Metadata-avain TODO</b></Grid>
          <Grid item xs={9}>Metadata-avaimen arvot TODO </Grid>
          <Grid item xs={3}><b>Lähetystunnus</b></Grid>
          <Grid item xs={9}>{lahetys.lahetysTunniste}</Grid>
          <Grid item xs={3}><b>Lähetyksen tila</b></Grid>
          <Grid item xs={9} display="flex" alignItems="center"><LahetysStatus tilat={lahetys.tilat || []}/></Grid>
          <Grid item xs={12}>
            <h2>Vastaanottajat</h2>
            <VirheAlert virheet={virheet}/>
            <LahetysStatus tilat={lahetys.tilat || []}/>
            <VastaanottajaHaku />
            <Suspense fallback={<Skeleton variant="rectangular" width={210} height={60} />}>
              <Vastaanottajat vastaanottajat={data.vastaanottajat || []}/>
              <VastaanottajatSivutus seuraavatAlkaen={data.seuraavatAlkaen} viimeisenTila={data.viimeisenTila}/>
            </Suspense>
          </Grid>
        </Grid>
      </div>
    )

  }
