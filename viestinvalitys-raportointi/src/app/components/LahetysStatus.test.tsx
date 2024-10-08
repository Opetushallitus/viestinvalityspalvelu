import { describe, expect, it, vi } from 'vitest';
import { VastaanotonTila } from '../lib/types';
import { LahetysStatus, StatusTeksti } from './LahetysStatus';
import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';

// mockataan font loader koska design system teeman import LahetysStatus-komponentissa tuotti ongelmia
// ks. https://github.com/vercel/next.js/issues/59701
vi.mock('next/font/google', () => ({
  Open_Sans: () => ({
    style: {
      fontFamily: 'mocked',
    },
  }),
}));

describe('LahetysStatus', async () => {
  const locale = 'fi';
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const messages = require(`../../i18n/messages/${locale}.json`);
  it('näyttää keskeneräisen tilan', () => {
    render(
      <NextIntlClientProvider messages={messages} locale={locale}>
        <LahetysStatus
          tilat={[
            { vastaanottotila: VastaanotonTila.SKANNAUS, vastaanottajaLkm: 2 },
            { vastaanottotila: VastaanotonTila.DELIVERY, vastaanottajaLkm: 3 },
          ]}
        />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText('2/5 viestin lähetys kesken')).toBeInTheDocument();
    expect(screen.getByTestId('WatchLaterIcon')).toBeDefined();
  });
  it('näyttää epäonnistuneen tilan', () => {
    render(
      <NextIntlClientProvider messages={messages} locale={locale}>
        <LahetysStatus
          tilat={[
            { vastaanottotila: VastaanotonTila.SKANNAUS, vastaanottajaLkm: 2 },
            { vastaanottotila: VastaanotonTila.REJECT, vastaanottajaLkm: 1 },
          ]}
        />
        ,
      </NextIntlClientProvider>,
    );
    expect(
      screen.getByText('1/3 viestin lähetys epäonnistui'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('ErrorIcon')).toBeDefined();
  });
  it('näyttää onnistuneen tilan', () => {
    render(
      <NextIntlClientProvider messages={messages} locale={locale}>
        <LahetysStatus
          tilat={[
            { vastaanottotila: VastaanotonTila.DELIVERY, vastaanottajaLkm: 2 },
          ]}
        />
        ,
      </NextIntlClientProvider>,
    );
    expect(
      screen.getByText('2/2 viestin lähetys onnistui'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('CheckCircleIcon')).toBeDefined();
  });
  it('näyttää tuntemattoman tilan', () => {
    render(
      <NextIntlClientProvider messages={messages} locale={locale}>
        <LahetysStatus tilat={[]} />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
    expect(screen.getByTestId('WarningIcon')).toBeDefined();
  });
});
describe('StatusTeksti', () => {
  const locale = 'fi';
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const messages = require(`../../i18n/messages/${locale}.json`);
  it('näyttää tyhjän tilan tekstin', () => {
    render(<NextIntlClientProvider messages={messages} locale={locale}><StatusTeksti tilat={[]} statusLocalized={'tuntematon'} /></NextIntlClientProvider>);
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
  });
  it('näyttää tuntemattoman tilan tekstin', () => {
    render(<NextIntlClientProvider messages={messages} locale={locale}><StatusTeksti tilat={undefined} statusLocalized={'tuntematon'} /></NextIntlClientProvider>);
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
  });
});
