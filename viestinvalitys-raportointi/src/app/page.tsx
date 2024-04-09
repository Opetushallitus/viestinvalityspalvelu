import { Suspense } from "react";
import Lahetykset from "./Lahetykset";
import { fetchLahetykset } from "./lib/data";
import Haku from "./Haku";
import LahetyksetSivutus from "./LahetyksetSivutus";
import { LahetysHakuParams } from "./lib/types";
import TableSkeleton from "./TableSkeleton";
import VirheAlert from "./components/VirheAlert";
import {createTranslation} from './i18n/server';

var fetchParams: LahetysHakuParams = {}
export default async function Page({
  searchParams,
}: {
  searchParams?: {
    hakukentta?: string
    hakusana?: string
    seuraavatAlkaen?: string
    organisaatio?: string
  }
}) {
  fetchParams = {
    seuraavatAlkaen: searchParams?.seuraavatAlkaen, 
    hakukentta: searchParams?.hakukentta, 
    hakusana: searchParams?.hakusana,
    organisaatio: searchParams?.organisaatio
  }
  console.info('kutsutaan lähetysten hakua')
  const data = await fetchLahetykset(fetchParams)
  const {t} = await createTranslation();
  const virheet = data?.virheet
  return (
    <main>
      <h1>{t('title')}</h1>
      <VirheAlert virheet={virheet}/>
      <Haku />
      <Suspense fallback={<TableSkeleton />}>
        <Lahetykset lahetykset={data.lahetykset || []}></Lahetykset>
        <LahetyksetSivutus seuraavatAlkaen={data.seuraavatAlkaen}/>
      </Suspense>
    </main>
  )
}
