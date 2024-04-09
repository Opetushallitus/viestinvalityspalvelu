import { Suspense } from 'react';
import DOMPurify from 'dompurify';
import { JSDOM } from 'jsdom';
import { Grid, Skeleton } from '@mui/material';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import { Warning } from '@mui/icons-material';
import { Lahetys } from '@/app/lib/types';
import LocalDateTime from '@/app/components/LocalDateTime';
import { LahetysStatus } from '@/app/components/LahetysStatus';
import VastaanottajaHaku from './VastaanottajaHaku';
import VastaanottajatSivutus from './VastaanottajatSivutus';
import VirheAlert from '@/app/components/VirheAlert';
import VastaanottajatTable from './Vastaanottajat';
import { fetchLahetyksenVastaanottajat, fetchLahetys, fetchMassaviesti } from '@/app/lib/data';
  
const LahetyksenTiedot = ({lahetys}: {lahetys: Lahetys}) => {
  return (
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
    </Grid>
  )
}

const MassaviestinTiedot = async ({lahetys}: {lahetys: Lahetys}) => {
  const viestiData = await fetchMassaviesti(lahetys.lahetysTunniste)
  const lahetysvirhe = viestiData?.virhe // TODO virhealert
  return (
    <Grid container spacing={2} padding={2}>
      <Grid item xs={12}> <h2>{viestiData.otsikko}</h2></Grid>
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
          {viestiData.sisallonTyyppi === 'HTML' ? 
            <div dangerouslySetInnerHTML={{__html: DOMPurify(new JSDOM('').window).sanitize(viestiData.sisalto)}}/>
           : <p>{viestiData.sisalto}</p>}
      </Grid>
    </Grid>
  )
}

  const Lahetys = async ({
    lahetys,
    searchParams,
  }: {
    lahetys: Lahetys;
    searchParams: any;
  }) => {
    const onMassaviesti = lahetys.viestiLkm === 1;
    console.info('kutsutaan vastaanottajahakua')
    console.timeStamp()
    const data = await fetchLahetyksenVastaanottajat(lahetys.lahetysTunniste, {
      alkaen: searchParams?.alkaen,
      sivutustila: searchParams?.sivutustila,
      hakukentta: searchParams?.hakukentta,
      hakusana: searchParams?.hakusana,
      tila: searchParams?.tila,
      organisaatio: searchParams?.organisaatio,
    });
    const virheet = data?.virheet;
    return (
      <Grid container spacing={2} padding={2}>
        {onMassaviesti ? (
          <MassaviestinTiedot lahetys={lahetys} />
        ) : (
          <LahetyksenTiedot lahetys={lahetys} />
        )}
        <Grid item xs={12}>
          <h2>Vastaanottajat</h2>
          <VirheAlert virheet={virheet} />
          <LahetysStatus tilat={lahetys?.tilat || []} />
          <VastaanottajaHaku />
          <Suspense
            fallback={
              <Skeleton variant="rectangular" width={210} height={60} />
            }
          >
            {data.vastaanottajat.length > 0 ? (
              <>
                <VastaanottajatTable
                  vastaanottajat={data.vastaanottajat}
                  onMassaviesti={onMassaviesti}
                />
                <VastaanottajatSivutus
                  seuraavatAlkaen={data.seuraavatAlkaen}
                  viimeisenTila={data.viimeisenTila}
                />
              </>
            ) : (
              <div>
                <FolderOutlinedIcon fontSize="large" />
                <p>Vastaanottajia ei löytynyt</p>
              </div>
            )}
          </Suspense>
        </Grid>
      </Grid>
    );
  };

  export default async function Page({
    params,
    searchParams,
  }: {
    params: { tunniste: string };
    searchParams?: {
      alkaen?: string;
      sivutustila?: string;
      hakukentta?: string;
      hakusana?: string;
      tila?: string;
      organisaatio?: string;
    };
  }) {
    console.info('kutsutaan lähetyksen hakua')
    console.timeStamp()
    const lahetysData = await fetchLahetys(params.tunniste);
    const lahetysvirhe = lahetysData?.virhe;
    return (
      <div>
        <h1>Lähetysraportti</h1>
        <VirheAlert virheet={lahetysvirhe ? [lahetysvirhe] : lahetysvirhe} />
        {lahetysData?.lahetysTunniste ? (
          <Lahetys lahetys={lahetysData} searchParams={searchParams} />
        ) : (
          <div>
            <Warning fontSize="large" />
            <p>Hakuehdoilla ei löytynyt tuloksia</p>
          </div>
        )}
      </div>
    );
  }
