import { Suspense } from 'react';
import DOMPurify from 'dompurify';
import { JSDOM } from 'jsdom';
import { Grid } from '@mui/material';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import { Warning } from '@mui/icons-material';
import { Lahetys, VastaanottajatHakuParams } from '@/app/lib/types';
import LocalDateTime from '@/app/components/LocalDateTime';
import { LahetysStatus } from '@/app/components/LahetysStatus';
import VastaanottajaHaku from './VastaanottajaHaku';
import VastaanottajatSivutus from './VastaanottajatSivutus';
import VirheAlert from '@/app/components/VirheAlert';
import VastaanottajatTable from './Vastaanottajat';
import {
  fetchLahetyksenVastaanottajat,
  fetchLahetys,
  fetchMassaviesti,
} from '@/app/lib/data';
import Loading from '@/app/components/Loading';
import { MainContainer } from '@/app/components/MainContainer';
import { GreyDivider } from '@/app/components/GreyDivider';
import { SearchParams } from 'nuqs/server';
import { searchParamsCache } from '@/app/lib/searchParams';
import { getTranslations } from 'next-intl/server';
const LahetyksenTiedot = async ({ lahetys }: { lahetys: Lahetys }) => {  

  const t = await getTranslations();
  return (
    <Grid container spacing={2} padding={2}>
      <Grid item xs={12}>
        {' '}
        <h2>{lahetys.otsikko}</h2>
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.ajankohta')}</b>
      </Grid>
      <Grid item xs={9}>
        <LocalDateTime date={lahetys.luotu} />
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.lahettaja')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.lahettajanSahkoposti}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.lahettaja-oid')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.lahettajanNimi ?? '-'},{' '}
        {lahetys.lahettavanVirkailijanOID ?? '-'}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.reply-to')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.replyTo}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.lahettava-palvelu')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.lahettavaPalvelu}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.tunnus')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.lahetysTunniste}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.tila')}</b>
      </Grid>
      <Grid item xs={9} display="flex" alignItems="center">
        <LahetysStatus tilat={lahetys.tilat ?? []} />
      </Grid>
    </Grid>
  );
};

const MassaviestinTiedot = async ({ lahetys }: { lahetys: Lahetys }) => {
  const viestiData = await fetchMassaviesti(lahetys.lahetysTunniste);
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const lahetysvirhe = viestiData?.virhe; // TODO virhealert
  const t = await getTranslations();
  return (
    <Grid container spacing={2} padding={2}>
      <Grid item xs={12}>
        {' '}
        <h2>{viestiData.otsikko}</h2>
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.ajankohta')}</b>
      </Grid>
      <Grid item xs={9}>
        <LocalDateTime date={lahetys.luotu} />
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.lahettaja')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.lahettajanNimi ?? ''},{' '}{lahetys.lahettajanSahkoposti}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.lahettaja-oid')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.lahettavanVirkailijanOID ?? '-'}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.reply-to')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.replyTo}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.lahettava-palvelu')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.lahettavaPalvelu}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.tunnus')}</b>
      </Grid>
      <Grid item xs={9}>
        {lahetys.lahetysTunniste}
      </Grid>
      <Grid item xs={3}>
        <b>{t('lahetys.tila')}</b>
      </Grid>
      <Grid item xs={9} display="flex" alignItems="center">
        <LahetysStatus tilat={lahetys.tilat ?? []} />
      </Grid>
      <Grid item xs={12}>
        {viestiData.sisallonTyyppi === 'HTML' ? (
          <div
            dangerouslySetInnerHTML={{
              __html: DOMPurify(new JSDOM('').window).sanitize(
                viestiData.sisalto,
              ),
            }}
          />
        ) : (
          <p>{viestiData.sisalto}</p>
        )}
      </Grid>
    </Grid>
  );
};

const LahetysView = async ({
  lahetys,
}: {
  lahetys: Lahetys;
}) => {
  const fetchParams: VastaanottajatHakuParams = {
    alkaen: searchParamsCache.get('alkaen'),
    hakukentta: searchParamsCache.get('hakukentta'),
    hakusana: searchParamsCache.get('hakusana'),
    tila: searchParamsCache.get('tila'),
    organisaatio: searchParamsCache.get('organisaatio'),
  }

  const onMassaviesti = lahetys.viestiLkm === 1;
  const data = await fetchLahetyksenVastaanottajat(lahetys.lahetysTunniste, fetchParams);
  const virheet = data?.virheet;
  const t = await getTranslations();
  return (
    <Grid container spacing={2} padding={2}>
      {onMassaviesti ? (
        <MassaviestinTiedot lahetys={lahetys} />
      ) : (
        <LahetyksenTiedot lahetys={lahetys} />
      )}
      <Grid item xs={12}>
      <GreyDivider />
        <h2>{t('vastaanottajat.otsikko')}</h2>
        <VirheAlert virheet={virheet} />
        <LahetysStatus tilat={lahetys?.tilat ?? []} />
        <Suspense fallback={<Loading />}>
          <VastaanottajaHaku />
          {data.vastaanottajat.length > 0 ? (
            <>
              <VastaanottajatTable
                vastaanottajat={data.vastaanottajat}
                onMassaviesti={onMassaviesti}
              />
              <VastaanottajatSivutus
                seuraavatAlkaen={data.seuraavatAlkaen}
              />
            </>
          ) : (
            <div>
              <FolderOutlinedIcon fontSize="large" />
              <p>{t('vastaanottajat.haku.eituloksia')}</p>
            </div>
          )}
        </Suspense>
      </Grid>
    </Grid>
  );
};

type PageProps = {
  params: { tunniste: string }
  searchParams: SearchParams
}

export default async function Page({ params, searchParams }: PageProps) {
  searchParamsCache.parse(searchParams) // pitää alustaa tässä jotta toimii LahetysView-komponentissa
  const lahetysData = await fetchLahetys(params.tunniste);
  const lahetysvirhe = lahetysData?.virhe;
  const t = await getTranslations();
  return (
    <MainContainer>
      <VirheAlert virheet={lahetysvirhe ? [lahetysvirhe] : lahetysvirhe} />
      {lahetysData?.lahetysTunniste ? (
        <LahetysView lahetys={lahetysData} />
      ) : (
        <div>
          <Warning fontSize="large" />
          <p>{t('haku.eituloksia')}</p>
        </div>
      )}
    </MainContainer>
  );
}
