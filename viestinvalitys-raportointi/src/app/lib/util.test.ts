import { expect, test } from 'vitest';
import {
  collectOrgsWithMatchingName,
  findOrganisaatioByOid,
  getLahetysStatus,
  getVastaanottajatPerStatus,
  lahetyksenStatus,
  parseExpandedParents,
} from './util';
import {
  Status,
  VastaanotonTila,
  LahetyksenVastaanottoTila,
  Organisaatio,
} from './types';

const onnistunutTila = [VastaanotonTila.DELIVERY];
const keskenTila = [VastaanotonTila.LAHETETTY];
const epaonnistunutTila = [VastaanotonTila.VIRHE];
test('Lähetys onnistui vain jos on pelkästään onnistuneita lähetystiloja'),
  () => {
    expect(getLahetysStatus(onnistunutTila)).toEqual(Status.ONNISTUI);
  };

test('Lähetys epäonnistui jos mukana on epäonnistunut lähetystila', () => {
  expect(getLahetysStatus(onnistunutTila.concat(epaonnistunutTila))).toEqual(
    Status.EPAONNISTUI
  );
  expect(
    getLahetysStatus(
      onnistunutTila.concat(epaonnistunutTila).concat(keskenTila)
    )
  ).toEqual(Status.EPAONNISTUI);
});

test('Lähetys on kesken jos on keskeneräisiä mutta ei epäonnistuneita', () => {
  expect(getLahetysStatus(keskenTila.concat(onnistunutTila))).toEqual(
    Status.KESKEN
  );
  expect(getLahetysStatus(keskenTila.concat(epaonnistunutTila))).toEqual(
    Status.EPAONNISTUI
  );
});

test('Vastaavassa tilassa olevien viestien lukumäärät summataan', () => {
  const keskenStatus = [
    {
      vastaanottotila: VastaanotonTila.LAHETETTY,
      vastaanottajaLkm: 1,
    },
  ];
  expect(
    getVastaanottajatPerStatus(
      keskenStatus.concat([
        {
          vastaanottotila: VastaanotonTila.ODOTTAA,
          vastaanottajaLkm: 2,
        },
      ])
    )
  ).toEqual(3);
  expect(
    getVastaanottajatPerStatus(
      keskenStatus.concat([
        {
          vastaanottotila: VastaanotonTila.ODOTTAA,
          vastaanottajaLkm: 2,
        },
        {
          vastaanottotila: VastaanotonTila.DELIVERY,
          vastaanottajaLkm: 2,
        },
      ])
    )
  ).toEqual(3);
});

test('Tyhjä lähetys osataan käsitellä', () => {
  expect(lahetyksenStatus(undefined)).toEqual(' ei viestejä/vastaanottajia');
  expect(lahetyksenStatus([])).toEqual(' ei viestejä/vastaanottajia');
});

test('ParentOidPathista parsitaan lista oideja', () => {
  expect(parseExpandedParents(undefined)).toEqual([]);
  expect(parseExpandedParents('')).toEqual([]);
  expect(parseExpandedParents('foo')).toEqual(['foo']);
  expect(
    parseExpandedParents(
      '1.2.246.562.10.90968727769/1.2.246.562.10.19085616498/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001'
    )
  ).toEqual([
    '1.2.246.562.10.90968727769',
    '1.2.246.562.10.19085616498',
    '1.2.246.562.10.240484683010',
    '1.2.246.562.10.00000000001',
  ]);
});

const orgs: Organisaatio[] = [
  {
    oid: '1.2.246.562.10.240484683010',
    parentOid: '1.2.246.562.10.00000000001',
    parentOidPath: '1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
    nimi: {
      fi: 'Itä-Suomen yliopisto',
      sv: 'Itä-Suomen yliopisto',
      en: 'Itä-Suomen yliopisto',
    },
    status: 'AKTIIVINEN',
    children: [
      {
        oid: '1.2.246.562.10.19085616498',
        parentOid: '1.2.246.562.10.240484683010',
        parentOidPath:
          '1.2.246.562.10.19085616498/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
        nimi: {
          fi: 'Tulliportin normaalikoulu',
          sv: 'Tulliportin normaalikoulu',
          en: 'Tulliportin normaalikoulu',
        },
        status: 'AKTIIVINEN',
        children: [
          {
            oid: '1.2.246.562.10.90968727769',
            parentOid: '1.2.246.562.10.19085616498',
            parentOidPath:
              '1.2.246.562.10.90968727769/1.2.246.562.10.19085616498/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
            nimi: {
              fi: 'Tulliportin normaalikoulu',
              sv: 'Tulliportin normaalikoulu',
              en: 'Tulliportin normaalikoulu',
            },
            status: 'AKTIIVINEN',
            children: [],
          },
        ]
      },
      {
        oid: '1.2.246.562.10.24835724865',
        parentOid: '1.2.246.562.10.240484683010',
        parentOidPath:
          '1.2.246.562.10.24835724865/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
        nimi: {
          fi: 'Rantakylän normaalikoulu',
          sv: 'Rantakylän normaalikoulu',
          en: 'Rantakylän normaalikoulu',
        },
        status: 'AKTIIVINEN',
        children: [],
      },
      {
        oid: '1.2.246.562.10.313555427010',
        parentOid: '1.2.246.562.10.38515028629',
        parentOidPath: '1.2.246.562.10.313555427010/1.2.246.562.10.38515028629/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
        nimi: {
          fi: 'Itä-Suomen yliopisto, Filosofinen tiedekunta',
          sv: 'Östra Finlands Universitet, Filosofiska fakulteten',
          en: 'University of Eastern Finland, Philosophical Faculty'
        },
        status: 'AKTIIVINEN',
        children: [
          {
            oid: '1.2.246.562.10.2014041814394599222321',
            parentOid: '1.2.246.562.10.313555427010',
            parentOidPath: '1.2.246.562.10.2014041814394599222321/1.2.246.562.10.313555427010/1.2.246.562.10.38515028629/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
            nimi: {
              fi: 'Itä-Suomen yliopisto, Filosofinen tiedekunta, Joensuun kampus',
              sv: 'Östra Finlands Universitet, Filosofiska fakulteten, Joensuun kampus',
              en: 'University of Eastern Finland, Philosophical Faculty, Joensuu campus'
            },
            status: 'AKTIIVINEN',
            children: [],
          }
        ]
      },
      {
        oid: '1.2.246.562.10.56730020288',
        parentOid: '1.2.246.562.10.38515028629',
        parentOidPath: '1.2.246.562.10.56730020288/1.2.246.562.10.38515028629/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
        nimi: {
          fi: 'Itä-Suomen yliopisto, Terveystieteiden tiedekunta',
          sv: 'Östra Finlands Universitet, Hälsovetenskapliga fakulteten',
          en: 'University of Eastern Finland, Faculty of Health Sciences'
        },
        status: 'AKTIIVINEN',
        children: [
          {
            oid: '1.2.246.562.10.2014041814440291927552',
            parentOid: '1.2.246.562.10.56730020288',
            parentOidPath: '1.2.246.562.10.2014041814440291927552/1.2.246.562.10.56730020288/1.2.246.562.10.38515028629/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
            nimi: {
              fi: 'Itä-Suomen yliopisto, Terveystieteiden tiedekunta, Kuopion kampus',
              sv: 'Östra Finlands Universitet, Hälsovetenskapliga fakulteten, Kuopion kampus',
              en: 'University of Eastern Finland, Faculty of Health Sciences, Kuopio campus'
            },
            status: 'AKTIIVINEN',
            children: [],
          },
          {
            oid: '1.2.246.562.10.46551141818',
            parentOid: '1.2.246.562.10.56730020288',
            parentOidPath: '1.2.246.562.10.46551141818/1.2.246.562.10.56730020288/1.2.246.562.10.38515028629/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001',
            nimi: {
              fi: 'Itä-Suomen yliopisto, Terveystieteiden tiedekunta, Ammatillinen jatkokoulutus',
              sv: 'Östra Finlands Universitet, Hälsovetenskapliga fakulteten, Ammatillinen jatkokoulutus',
              en: 'University of Eastern Finland, Faculty of Health Sciences, Ammatillinen jatkokoulutus'
            },
            status: 'AKTIIVINEN',
            children: [],
          }
        ]
      },
    ],
  },
];

test('Hae organisaatio oidilla', () => {
  const existingOrg = findOrganisaatioByOid(orgs, '1.2.246.562.10.90968727769');
  expect(existingOrg).toBeDefined
  expect(existingOrg?.nimi?.fi).toEqual('Tulliportin normaalikoulu')
  const nonExistingOrg = findOrganisaatioByOid(orgs, 'foo');
  expect(nonExistingOrg).not.toBeDefined;
});

test('Poimi hakukriteeriä vastaavat oidit', () => {
  const result: { oid: string; parentOidPath: string }[] = [];

  collectOrgsWithMatchingName(orgs, 'normaali', result)
  console.log(result)
  const expected = [
    {oid: '1.2.246.562.10.19085616498', parentOidPath: '1.2.246.562.10.19085616498/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001'},
    {oid: '1.2.246.562.10.90968727769', parentOidPath: '1.2.246.562.10.90968727769/1.2.246.562.10.19085616498/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001'},
    {oid: '1.2.246.562.10.24835724865', parentOidPath: '1.2.246.562.10.24835724865/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001'},
  ]
  expect(result).toEqual(expected);
});
