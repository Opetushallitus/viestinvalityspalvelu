import { expect, test } from 'vitest'
import { getLahetysStatus, getVastaanottajatPerStatus, lahetyksenStatus, parseExpandedParents } from './util';
import { Status, VastaanotonTila, LahetyksenVastaanottoTila } from './types';

const onnistunutTila = [VastaanotonTila.DELIVERY]
const keskenTila = [VastaanotonTila.LAHETETTY]
const epaonnistunutTila = [VastaanotonTila.VIRHE]
test('Lähetys onnistui vain jos on pelkästään onnistuneita lähetystiloja'), () => {
    expect(getLahetysStatus(onnistunutTila)).toEqual(Status.ONNISTUI);
}

test('Lähetys epäonnistui jos mukana on epäonnistunut lähetystila', () => {
    expect(getLahetysStatus(onnistunutTila.concat(epaonnistunutTila))).toEqual(Status.EPAONNISTUI);
    expect(getLahetysStatus(onnistunutTila.concat(epaonnistunutTila).concat(keskenTila))).toEqual(Status.EPAONNISTUI);
});

test('Lähetys on kesken jos on keskeneräisiä mutta ei epäonnistuneita', () => {
    expect(getLahetysStatus(keskenTila.concat(onnistunutTila))).toEqual(Status.KESKEN);
    expect(getLahetysStatus(keskenTila.concat(epaonnistunutTila))).toEqual(Status.EPAONNISTUI);
});

test('Vastaavassa tilassa olevien viestien lukumäärät summataan', () => {
    const keskenStatus = [
        {
          vastaanottotila: VastaanotonTila.LAHETETTY,
          vastaanottajaLkm: 1
        }
    ]
    expect(getVastaanottajatPerStatus(keskenStatus.concat([
        {
          vastaanottotila: VastaanotonTila.ODOTTAA,
          vastaanottajaLkm: 2
        }
    ]))).toEqual(3);
    expect(getVastaanottajatPerStatus(keskenStatus.concat([
        {
          vastaanottotila: VastaanotonTila.ODOTTAA,
          vastaanottajaLkm: 2
        },
        {
            vastaanottotila: VastaanotonTila.DELIVERY,
            vastaanottajaLkm: 2
        }
    ]))).toEqual(3);
});

test('Tyhjä lähetys osataan käsitellä', () => {
    expect(lahetyksenStatus(undefined)).toEqual(' ei viestejä/vastaanottajia')
    expect(lahetyksenStatus([])).toEqual(' ei viestejä/vastaanottajia')
});

test('ParentOidPathista parsitaan lista oideja', () => {
    expect(parseExpandedParents(undefined)).toEqual([])
    expect(parseExpandedParents('')).toEqual([])
    expect(parseExpandedParents('foo')).toEqual(['foo'])
    expect(parseExpandedParents('1.2.246.562.10.90968727769/1.2.246.562.10.19085616498/1.2.246.562.10.240484683010/1.2.246.562.10.00000000001'))
    .toEqual(['1.2.246.562.10.90968727769','1.2.246.562.10.19085616498','1.2.246.562.10.240484683010','1.2.246.562.10.00000000001'])
});