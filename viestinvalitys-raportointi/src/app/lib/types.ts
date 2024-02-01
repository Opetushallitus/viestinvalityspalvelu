export type LahetysHakuParams = {
    seuraavatAlkaen?: string
    hakukentta?: string
    hakusana?: string 
}

export type VastaanottajaTila = {
    vastaanottotila: VastaanotonTila
    vastaanottajaLkm: number
}

// Näiden pitää täsmätä viestinvälityspalvelun enumiin VastaanottajanTila
export enum VastaanotonTila {
    SKANNAUS = 'SKANNAUS', 
    ODOTTAA = 'ODOTTAA', 
    LAHETYKSESSA = 'LAHETYKSESSA', 
    VIRHE = 'VIRHE', 
    LAHETETTY = 'LAHETETTY', 
    SEND = 'SEND', 
    DELIVERY = 'DELIVERY', 
    BOUNCE = 'BOUNCE', 
    COMPLAINT = 'COMPLAINT', 
    REJECT = 'REJECT', 
    DELIVERYDELAY = 'DELIVERYDELAY'
}

export enum Status {
    EPAONNISTUI = 'epäonnistui',
    KESKEN = 'kesken',
    ONNISTUI = 'onnistui'
}

export const ONNISTUNEET_TILAT = [
    VastaanotonTila.DELIVERY
]

export const EPAONNISTUNEET_TILAT = [
    VastaanotonTila.BOUNCE,
    VastaanotonTila.COMPLAINT,
    VastaanotonTila.REJECT,
    VastaanotonTila.VIRHE
]

export const KESKENERAISET_TILAT = [
    VastaanotonTila.DELIVERYDELAY,
    VastaanotonTila.LAHETETTY,
    VastaanotonTila.LAHETYKSESSA,
    VastaanotonTila.ODOTTAA,
    VastaanotonTila.SEND,
    VastaanotonTila.SKANNAUS
]

export type Lahetys = {
    lahetysTunniste: string
    otsikko: string
    omistaja: string
    lahettavaPalvelu: string
    lahettavanVirkailijanOID?: string 
    lahettajanNimi?: string 
    lahettajanSahkoposti: string
    replyTo: string
    luotu: string
    tilat?: VastaanottajaTila[]
  }

  export type Viesti = {
    tunniste: string
    otsikko: string
    omistaja: string
    sisalto: string
    sisallonTyyppi: 'text' | 'html' 
    kielet: string[] // TODO tyypitys
    // TODO maskit
    lahettavaPalvelu: string
    lahettavanVirkailijanOID?: string 
    lahettajanNimi?: string 
    lahettajanSahkoposti: string
    replyTo?: string
  }

  export type Vastaanottaja = {
    tunniste: string
    nimi: string
    sahkoposti: string
    viestiTunniste: string
    tila: string // TODO tyypitys
  }
