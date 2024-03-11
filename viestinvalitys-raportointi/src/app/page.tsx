import { Suspense } from "react";
import Lahetykset from "./Lahetykset";
import { fetchLahetykset, fetchOrganisaatioHierarkia } from "./lib/data";
import Haku from "./Haku";
import LahetyksetSivutus from "./LahetyksetSivutus";
import { LahetysHakuParams, Organisaatio } from "./lib/types";
import TableSkeleton from "./TableSkeleton";
import VirheAlert from "./components/VirheAlert";


var fetchParams: LahetysHakuParams = {}
export default async function Page({
  searchParams,
}: {
  searchParams?: {
    hakukentta?: string
    hakusana?: string
    seuraavatAlkaen?: string
  }
}) {
  fetchParams = {
    seuraavatAlkaen: searchParams?.seuraavatAlkaen, 
    hakukentta: searchParams?.hakukentta, 
    hakusana: searchParams?.hakusana
  }
  const data = await fetchLahetykset(fetchParams)
  const virheet = data?.virheet
  return (
    <main>
      <h1>Viestien raportit</h1>
      <VirheAlert virheet={virheet}/>
      <Haku />
      <Suspense fallback={<TableSkeleton />}>
        <Lahetykset lahetykset={data.lahetykset || []}></Lahetykset>
        <LahetyksetSivutus seuraavatAlkaen={data.seuraavatAlkaen}/>
      </Suspense>
    </main>
  )
}
