import { expect, test } from 'vitest'
import { getLahetysStatus, getVastaanottajatPerStatus, lahetyksenStatus } from './util';
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

test('Tyhjä lähetys osataan käsitellä'), () => {
    expect(lahetyksenStatus(undefined)).toEqual('-')
    expect(lahetyksenStatus(undefined)).toEqual('-')
}