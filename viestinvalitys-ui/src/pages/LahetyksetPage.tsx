import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { MainContainer } from '../components/MainContainer';
import { fetchLahetykset, fetchLahettavatPalvelut } from '../lib/api';
import { LahetysHakuParams } from '../lib/types';
import Haku from '../components/Haku';
import LahetyksetTable from '../components/LahetyksetTable';
import LahetyksetSivutus from '../components/LahetyksetSivutus';
import Loading from '../components/Loading';
import { NoResults } from '../components/no-results';
import VirheAlert from '../components/VirheAlert';

export default function LahetyksetPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();

  const hakuParams: LahetysHakuParams = {
    seuraavatAlkaen: searchParams.get('seuraavatAlkaen'),
    hakusana: searchParams.get('hakusana'),
    palvelu: searchParams.get('palvelu'),
    hakuAlkaen: searchParams.get('hakuAlkaen'),
    hakuPaattyen: searchParams.get('hakuPaattyen'),
    organisaatio: searchParams.get('organisaatio'),
  };

  const lahetyksetQuery = useQuery({
    queryKey: ['lahetykset', hakuParams],
    queryFn: () => fetchLahetykset(hakuParams),
  });

  const palvelutQuery = useQuery({
    queryKey: ['palvelut'],
    queryFn: fetchLahettavatPalvelut,
    staleTime: 5 * 60 * 1000,
  });

  return (
    <MainContainer>
      <Haku lahettavatPalvelut={palvelutQuery.data ?? []} />
      {lahetyksetQuery.isLoading ? (
        <Loading />
      ) : lahetyksetQuery.isError ? (
        <VirheAlert virheet={[t('error.fetch')]} />
      ) : (
        <>
          {(lahetyksetQuery.data?.lahetykset?.length ?? 0) > 0 ? (
            <LahetyksetTable lahetykset={lahetyksetQuery.data.lahetykset} />
          ) : (
            <NoResults text={t('lahetykset.haku.eituloksia')} />
          )}
          <LahetyksetSivutus sivutusAlkaenParam={lahetyksetQuery.data?.seuraavatAlkaen ?? null} />
        </>
      )}
    </MainContainer>
  );
}
