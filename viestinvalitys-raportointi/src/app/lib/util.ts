import { EPAONNISTUNEET_TILAT, KESKENERAISET_TILAT, ONNISTUNEET_TILAT, Status, VastaanotonTila, LahetyksenVastaanottoTila } from "./types"


export const getLahetyksenVastaanottajia = (tilat: LahetyksenVastaanottoTila[]): number => {
    return tilat.map(tila => tila.vastaanottajaLkm).reduce(function(a, b)
    { return a + b;})
  }

export const lahetyksenStatus = (tilat: LahetyksenVastaanottoTila[] | undefined): string => {
    if(!tilat || tilat.length<1) {
      return ' ei viestejä/vastaanottajia'
    }
    const status = `${getVastaanottajatPerStatus(tilat)}/${getLahetyksenVastaanottajia(tilat)} viestin lähetys ${getLahetysStatus(tilat.map(tila => tila.vastaanottotila))}`
    return status
}

export const getLahetysStatus = (tilat: VastaanotonTila[]): string => {
    if(tilat.filter(tila => EPAONNISTUNEET_TILAT.includes(tila)).length > 0) {
        return Status.EPAONNISTUI
    }
    if(tilat.filter(tila => KESKENERAISET_TILAT.includes(tila)).length>0) {
        return Status.KESKEN
    }
    if(tilat.filter(tila => ONNISTUNEET_TILAT.includes(tila)).length > 0) {
        return Status.ONNISTUI
    }
    return 'tuntematon tila'
}

export const getVastaanottajatPerStatus = (tilat: LahetyksenVastaanottoTila[]): number => {
    const lahetysStatus = getLahetysStatus(tilat.map(tila => tila.vastaanottotila))
    if(lahetysStatus==='onnistui') {
        return tilat.filter(tila => ONNISTUNEET_TILAT.includes(tila.vastaanottotila))
        .map(tila => tila.vastaanottajaLkm).reduce(function(a, b)
        { return a + b;})
    }
    if(lahetysStatus==='epäonnistui') {
        return tilat.filter(tila => EPAONNISTUNEET_TILAT.includes(tila.vastaanottotila))
        .map(tila => tila.vastaanottajaLkm).reduce(function(a, b)
        { return a + b;})
    }
    if(lahetysStatus==='kesken') {
        return tilat.filter(tila => KESKENERAISET_TILAT.includes(tila.vastaanottotila))
        .map(tila => tila.vastaanottajaLkm).reduce(function(a, b)
        { return a + b;})
    }
    return 0
}

export const parseExpandedParents = (parentOidPath: string): string[] => {
    if (!parentOidPath || parentOidPath.length < 1) {
        return []
    }
    return parentOidPath.split('/')
}