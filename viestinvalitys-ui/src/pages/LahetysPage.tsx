import { useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import Grid from '@mui/material/Grid2';
import { Warning } from '@mui/icons-material';
import { MainContainer } from '../components/MainContainer';
import { fetchLahetys, fetchLahetyksenVastaanottajat, fetchMassaviesti } from '../lib/api';
import { Lahetys, VastaanottajatHakuParams } from '../lib/types';
import { LahetysStatus } from '../components/LahetysStatus';
import LocalDateTime from '../components/LocalDateTime';
import Loading from '../components/Loading';
import { GreyDivider } from '../components/GreyDivider';
import { NoResults } from '../components/no-results';
import VastaanottajaHaku from '../components/VastaanottajaHaku';
import VastaanottajatTable from '../components/Vastaanottajat';
import VastaanottajatSivutus from '../components/VastaanottajatSivutus';
import { SanitizedHtml } from '../components/SanitizedHtmlComponent';

function LahetyksenTiedot({ lahetys }: { lahetys: Lahetys }) {
  const { t } = useTranslation();
  return (
    <Grid container spacing={2} padding={2}>
      <Grid size={12}>
        <h2>{lahetys.otsikko}</h2>
      </Grid>
      <Grid size={3}>
        <b>{t('lahetys.ajankohta')}</b>
      </Grid>
      <Grid size={9}>
        <LocalDateTime date={lahetys.luotu} />
      </Grid>
      <Grid size={3}>
        <b>{t('lahetys.lahettaja')}</b>
      </Grid>
      <Grid size={9}>{lahetys.lahettajanSahkoposti}</Grid>
      <Grid size={3}>
        <b>{t('lahetys.lahettaja-oid')}</b>
      </Grid>
      <Grid size={9}>
        {lahetys.lahettajanNimi ?? '-'}, {lahetys.lahettavanVirkailijanOID ?? '-'}
      </Grid>
      <Grid size={3}>
        <b>{t('lahetys.reply-to')}</b>
      </Grid>
      <Grid size={9}>{lahetys.replyTo}</Grid>
      <Grid size={3}>
        <b>{t('lahetys.lahettava-palvelu')}</b>
      </Grid>
      <Grid size={9}>{lahetys.lahettavaPalvelu}</Grid>
      <Grid size={3}>
        <b>{t('lahetys.tunnus')}</b>
      </Grid>
      <Grid size={9}>{lahetys.lahetysTunniste}</Grid>
      <Grid size={3}>
        <b>{t('lahetys.tila')}</b>
      </Grid>
      <Grid size={9} display="flex" alignItems="center">
        <LahetysStatus tilat={lahetys.tilat ?? []} />
      </Grid>
    </Grid>
  );
}

function MassaviestinTiedot({
  lahetys,
  viestiData,
}: {
  lahetys: Lahetys;
  viestiData: { otsikko: string; sisalto: string; sisallonTyyppi: string };
}) {
  const { t } = useTranslation();
  return (
    <Grid container spacing={2} padding={2}>
      <Grid size={12}>
        <h2>{viestiData.otsikko}</h2>
      </Grid>
      <Grid size={3}>
        <b>{t('lahetys.ajankohta')}</b>
      </Grid>
      <Grid size={9}>
        <LocalDateTime date={lahetys.luotu} />
      </Grid>
      <Grid size={3}>
        <b>{t('lahetys.lahettaja')}</b>
      </Grid>
      <Grid size={9}>
        {lahetys.lahettajanNimi ?? ''}, {lahetys.lahettajanSahkoposti}
      </Grid>
      <Grid size={3}>
        <b>{t('lahetys.lahettaja-oid')}</b>
      </Grid>
      <Grid size={9}>{lahetys.lahettavanVirkailijanOID ?? '-'}</Grid>
      <Grid size={3}>
        <b>{t('lahetys.reply-to')}</b>
      </Grid>
      <Grid size={9}>{lahetys.replyTo}</Grid>
      <Grid size={3}>
        <b>{t('lahetys.lahettava-palvelu')}</b>
      </Grid>
      <Grid size={9}>{lahetys.lahettavaPalvelu}</Grid>
      <Grid size={3}>
        <b>{t('lahetys.tunnus')}</b>
      </Grid>
      <Grid size={9}>{lahetys.lahetysTunniste}</Grid>
      <Grid size={3}>
        <b>{t('lahetys.tila')}</b>
      </Grid>
      <Grid size={9} display="flex" alignItems="center">
        <LahetysStatus tilat={lahetys.tilat ?? []} />
      </Grid>
      <Grid size={12}>
        {viestiData.sisallonTyyppi === 'HTML' ? (
          <SanitizedHtml html={viestiData.sisalto} />
        ) : (
          <p>{viestiData.sisalto}</p>
        )}
      </Grid>
    </Grid>
  );
}

export default function LahetysPage() {
  const { tunniste } = useParams<{ tunniste: string }>();
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();

  const lahetysQuery = useQuery({
    queryKey: ['lahetys', tunniste],
    queryFn: () => fetchLahetys(tunniste!),
    enabled: !!tunniste,
  });

  const lahetys: Lahetys | undefined = lahetysQuery.data?.lahetysTunniste
    ? lahetysQuery.data
    : undefined;
  const onMassaviesti = lahetys?.viestiLkm === 1;

  const massaviestQuery = useQuery({
    queryKey: ['massaviesti', tunniste],
    queryFn: () => fetchMassaviesti(tunniste!),
    enabled: !!lahetys && onMassaviesti,
  });

  const hakuParams: VastaanottajatHakuParams = {
    alkaen: searchParams.get('alkaen'),
    hakusana: searchParams.get('hakusana'),
    tila: searchParams.get('tila'),
    organisaatio: searchParams.get('organisaatio'),
  };

  const vastaanottajatQuery = useQuery({
    queryKey: ['vastaanottajat', tunniste, hakuParams],
    queryFn: () => fetchLahetyksenVastaanottajat(tunniste!, hakuParams),
    enabled: !!lahetys,
  });

  if (lahetysQuery.isLoading)
    return (
      <MainContainer>
        <Loading />
      </MainContainer>
    );

  if (lahetysQuery.isError || !lahetys) {
    return (
      <MainContainer>
        <div>
          <Warning fontSize="large" />
          <p>{t('error.notfound.teksti')}</p>
        </div>
      </MainContainer>
    );
  }

  return (
    <MainContainer>
      {onMassaviesti && massaviestQuery.data ? (
        <MassaviestinTiedot lahetys={lahetys} viestiData={massaviestQuery.data} />
      ) : (
        <LahetyksenTiedot lahetys={lahetys} />
      )}
      <Grid container spacing={2} padding={2}>
        <Grid size={12}>
          <GreyDivider />
          <h2>{t('vastaanottajat.otsikko')}</h2>
          <LahetysStatus tilat={lahetys.tilat ?? []} />
          <VastaanottajaHaku />
          {vastaanottajatQuery.isLoading ? (
            <Loading />
          ) : (vastaanottajatQuery.data?.vastaanottajat?.length ?? 0) > 0 ? (
            <>
              <VastaanottajatTable
                vastaanottajat={vastaanottajatQuery.data.vastaanottajat}
                onMassaviesti={onMassaviesti ?? false}
                downloadEnabled={false}
              />
              <VastaanottajatSivutus
                sivutusAlkaenParam={vastaanottajatQuery.data?.seuraavatAlkaen ?? null}
              />
            </>
          ) : (
            <NoResults text={t('vastaanottajat.haku.eituloksia')} />
          )}
        </Grid>
      </Grid>
    </MainContainer>
  );
}
