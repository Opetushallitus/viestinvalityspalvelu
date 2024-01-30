import { Suspense } from "react";
import Lahetykset from "./Lahetykset";
import { fetchLahetykset } from "./lib/data";
import Haku from "./Haku";
import LahetyksetSivutus from "./LahetyksetSivutus";
import { LahetysHakuParams } from "./lib/types";
import TableSkeleton from "./TableSkeleton";


var fetchParams: LahetysHakuParams = {}
export default async function Page({
  searchParams,
}: {
  searchParams?: {
    hakukentta?: string
    hakusana?: string
    seuraava?: string
  }
}) {
  const hakusana = searchParams?.hakusana || '';
  console.log(hakusana)
  if(searchParams?.seuraava) {
    fetchParams = {...fetchParams, seuraavatAlkaen: searchParams.seuraava}
  }
  const data = await fetchLahetykset(fetchParams)
  return (
    <main>
      <h1>Viestien raportit</h1>
      <Haku />
      <Suspense fallback={<TableSkeleton />}>
        <Lahetykset lahetykset={data.lahetykset}></Lahetykset>
        <LahetyksetSivutus seuraavatAlkaen={data.seuraavatAlkaen}/>
      </Suspense>
    </main>
  )
}
