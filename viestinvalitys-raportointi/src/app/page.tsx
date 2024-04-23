import { Suspense } from 'react';
import { fetchLahetykset } from './lib/data';
import Haku from './Haku';
import LahetyksetSivutus from './LahetyksetSivutus';
import { LahetysHakuParams } from './lib/types';
import Loading from './components/Loading';
import VirheAlert from './components/VirheAlert';
import { createTranslation } from './i18n/server';
import Paper from '@mui/material/Paper';
import FolderOutlinedIcon from '@mui/icons-material/FolderOutlined';
import LahetyksetTable from './Lahetykset';

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
      <Paper sx={{ width: '100%', overflow: 'hidden' }}>
        {data.lahetykset?.length > 0 ? (
          <LahetyksetTable lahetykset={data.lahetykset} />
        ) : (
          <div>
            <FolderOutlinedIcon fontSize="large" />
            <p>Hakuehdoilla ei löytynyt tuloksia</p>
          </div>
        )}
      </Paper>
      <LahetyksetSivutus seuraavatAlkaen={data.seuraavatAlkaen} />
    </>
  );
};

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
  var fetchParams: LahetysHakuParams = {
    seuraavatAlkaen: searchParams?.seuraavatAlkaen,
    hakukentta: searchParams?.hakukentta,
    hakusana: searchParams?.hakusana,
    organisaatio: searchParams?.organisaatio,
  };

  const { t } = await createTranslation();

  return (
    <main>
      <h1>{t('title')}</h1>
      <Haku />
      <Suspense fallback={<Loading />}>
        <Lahetykset fetchParams={fetchParams} />
      </Suspense>
    </main>
  );
}
