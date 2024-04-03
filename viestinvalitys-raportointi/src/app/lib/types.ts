export type LahetysHakuParams = {
  seuraavatAlkaen?: string;
  hakukentta?: string;
  hakusana?: string;
  organisaatio?: string;
};

export type VastaanottajatHakuParams = {
  alkaen?: string;
  sivutustila?: string;
  hakukentta?: string;
  hakusana?: string;
  tila?: string;
  organisaatio?: string;
};

export type LahetyksenVastaanottoTila = {
  vastaanottotila: VastaanotonTila;
  vastaanottajaLkm: number;
};

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
  DELIVERYDELAY = 'DELIVERYDELAY',
}

export enum Status {
  EPAONNISTUI = 'epäonnistui',
  KESKEN = 'kesken',
  ONNISTUI = 'onnistui',
}

export const ONNISTUNEET_TILAT = [VastaanotonTila.DELIVERY];

export const EPAONNISTUNEET_TILAT = [
  VastaanotonTila.BOUNCE,
  VastaanotonTila.COMPLAINT,
  VastaanotonTila.REJECT,
  VastaanotonTila.VIRHE,
];

export const KESKENERAISET_TILAT = [
  VastaanotonTila.DELIVERYDELAY,
  VastaanotonTila.LAHETETTY,
  VastaanotonTila.LAHETYKSESSA,
  VastaanotonTila.ODOTTAA,
  VastaanotonTila.SEND,
  VastaanotonTila.SKANNAUS,
];

export type Lahetys = {
  lahetysTunniste: string;
  otsikko: string;
  omistaja: string;
  lahettavaPalvelu: string;
  lahettavanVirkailijanOID?: string;
  lahettajanNimi?: string;
  lahettajanSahkoposti: string;
  replyTo: string;
  luotu: string;
  tilat?: LahetyksenVastaanottoTila[];
  viestiLkm: number;
};

export type Viesti = {
  tunniste: string;
  otsikko: string;
  omistaja: string;
  sisalto: string;
  sisallonTyyppi: 'text' | 'html';
  kielet: LanguageCode[];
};

export type Vastaanottaja = {
  tunniste: string;
  nimi: string;
  sahkoposti: string;
  viestiTunniste: string;
  tila: VastaanotonTila;
};

export type LanguageCode = 'fi' | 'sv' | 'en';

export type Organisaatio = {
  oid: string;
  parentOid: string;
  parentOidPath: string;
  nimi: Record<LanguageCode, string>;
  status: string;
  children: Organisaatio[];
};
