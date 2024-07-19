import { Suspense } from 'react';
import { fetchLahetykset } from './lib/data';
import Haku from './Haku';
import LahetyksetSivutus from './LahetyksetSivutus';
import { LahetysHakuParams } from './lib/types';
import Loading from './components/Loading';
import VirheAlert from './components/VirheAlert';
import { createTranslation } from './i18n/server';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import LahetyksetTable from './Lahetykset';
import { MainContainer } from './components/MainContainer';

const Lahetykset = async ({
  fetchParams,
}: {
  fetchParams: LahetysHakuParams;
}) => {
  const data = await fetchLahetykset(fetchParams);
  const virheet = data?.virheet;
  return (
    <>
      <VirheAlert virheet={virheet} />
        {data.lahetykset?.length > 0 ? (
          <LahetyksetTable lahetykset={data.lahetykset} />
        ) : (
          <div>
            <FolderOutlinedIcon fontSize="large" />
            <p>Hakuehdoilla ei löytynyt tuloksia</p>
          </div>
        )}
      <LahetyksetSivutus seuraavatAlkaen={data.seuraavatAlkaen} />
    </>
  );
};
// eslint-disable-next-line no-var
var fetchParams: LahetysHakuParams = {};
export default async function Page({
  searchParams,
}: {
  searchParams?: {
    hakukentta?: string;
    hakusana?: string;
    seuraavatAlkaen?: string;
    organisaatio?: string;
  };
}) {
  fetchParams = {
    seuraavatAlkaen: searchParams?.seuraavatAlkaen,
    hakukentta: searchParams?.hakukentta,
    hakusana: searchParams?.hakusana,
    organisaatio: searchParams?.organisaatio,
  };

  const { t } = await createTranslation();

  return (
    <MainContainer>
      <h1>{t('title')}</h1>
      <Suspense fallback={<Loading />}>
        <Haku />
        <Lahetykset fetchParams={fetchParams} />
      </Suspense>
    </MainContainer>
  );
}
