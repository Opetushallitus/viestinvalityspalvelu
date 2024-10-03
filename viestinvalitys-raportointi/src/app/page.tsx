import { Suspense } from 'react';
import { SearchParams } from 'nuqs/server';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import { searchParamsCache } from './lib/searchParams';
import Haku from './Haku';
import Loading from './components/Loading';
import { MainContainer } from './components/MainContainer';
import VirheAlert from './components/VirheAlert';
import LahetyksetTable from './LahetyksetTable';
import LahetyksetSivutus from './LahetyksetSivutus';
import { LahetysHakuParams } from './lib/types';
import { fetchLahettavatPalvelut, fetchLahetykset } from './lib/data';
import { getLocale, initTranslations } from './i18n/localization';

const Lahetykset = async () => {
  const fetchParams: LahetysHakuParams = {
    seuraavatAlkaen: searchParamsCache.get('seuraavatAlkaen'),
    hakukentta: searchParamsCache.get('hakukentta'),
    hakusana: searchParamsCache.get('hakusana'),
    palvelu: searchParamsCache.get('palvelu'),
    hakuAlkaen: searchParamsCache.get('hakuAlkaen')?.toISOString() ?? null,
    hakuPaattyen: searchParamsCache.get('hakuPaattyen')?.toISOString() ?? null,
    organisaatio: searchParamsCache.get('organisaatio'),
  }
  const data = await fetchLahetykset(fetchParams);
  const virheet = data?.virheet;
  const { t } = await initTranslations();
  return (
    <>
      <VirheAlert virheet={virheet} />
        {data.lahetykset?.length > 0 ? (
          <LahetyksetTable lahetykset={data.lahetykset} />
        ) : (
          <div>
            <FolderOutlinedIcon fontSize="large" />
            <p>{t('lahetykset.haku.eituloksia')}</p>
          </div>
        )}
      <LahetyksetSivutus seuraavatAlkaen={data.seuraavatAlkaen} />
    </>
  );
};

type PageProps = {
  searchParams: SearchParams
}

export default async function Page({ searchParams }: PageProps) {

  searchParamsCache.parse(searchParams) // pitää alustaa tässä jotta toimii lahetykset-komponentissa
  const palvelut = await fetchLahettavatPalvelut();
  const locale = await getLocale();
  return (
    <MainContainer>
      <Suspense fallback={<Loading />}>
        <Haku lahettavatPalvelut={palvelut} locale={locale} />
        <Lahetykset />
      </Suspense>
    </MainContainer>
  );
}
