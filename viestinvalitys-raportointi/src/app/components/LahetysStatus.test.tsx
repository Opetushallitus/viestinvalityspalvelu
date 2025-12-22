import { render, RenderResult } from 'vitest-browser-react';
import { describe, expect, it, vi } from 'vitest';
import { VastaanotonTila } from '../lib/types';
import { LahetysStatus, StatusTeksti } from './LahetysStatus';
import { NextIntlClientProvider } from 'next-intl';

import fi from '../../i18n/messages/fi.json';

// mockataan font loader koska design system teeman import LahetysStatus-komponentissa tuotti ongelmia
// ks. https://github.com/vercel/next.js/issues/59701
vi.mock('next/font/google', () => ({
  Open_Sans: () => ({
    style: {
      fontFamily: 'mocked',
    },
  }),
}));

const renderWithLocalizationProvider = async (
  children: React.ReactNode,
): Promise<RenderResult> => {
  const locale = 'fi';
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return render(
    <NextIntlClientProvider messages={fi} locale={locale}>
      {children}
    </NextIntlClientProvider>,
  );
};

describe('LahetysStatus', async () => {
  it('näyttää keskeneräisen tilan', async () => {
    const screen = await renderWithLocalizationProvider(
      <LahetysStatus
        tilat={[
          { vastaanottotila: VastaanotonTila.SKANNAUS, vastaanottajaLkm: 2 },
          { vastaanottotila: VastaanotonTila.DELIVERY, vastaanottajaLkm: 3 },
        ]}
      />,
    );
    expect(screen.getByText('2/5 viestin lähetys kesken')).toBeInTheDocument();
    expect(screen.getByTestId('WatchLaterIcon')).toBeDefined();
  });
  it('näyttää epäonnistuneen tilan', async () => {
    const screen = await renderWithLocalizationProvider(
      <LahetysStatus
        tilat={[
          { vastaanottotila: VastaanotonTila.SKANNAUS, vastaanottajaLkm: 2 },
          { vastaanottotila: VastaanotonTila.REJECT, vastaanottajaLkm: 1 },
        ]}
      />,
    );
    expect(
      screen.getByText('1/3 viestin lähetys epäonnistui'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('ErrorIcon')).toBeDefined();
  });
  it('näyttää onnistuneen tilan', async () => {
    const screen = await renderWithLocalizationProvider(
      <LahetysStatus
        tilat={[
          { vastaanottotila: VastaanotonTila.DELIVERY, vastaanottajaLkm: 2 },
        ]}
      />,
    );
    expect(
      screen.getByText('2/2 viestin lähetys onnistui'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('CheckCircleIcon')).toBeDefined();
  });
  it('näyttää tuntemattoman tilan', async () => {
    const screen = await renderWithLocalizationProvider(
      <LahetysStatus tilat={[]} />,
    );
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
    expect(screen.getByTestId('WarningIcon')).toBeDefined();
  });
});
describe('StatusTeksti', () => {
  it('näyttää tyhjän tilan tekstin', async () => {
    const screen = await renderWithLocalizationProvider(
      <StatusTeksti tilat={[]} statusLocalized={'tuntematon'} />,
    );
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
  });
  it('näyttää tuntemattoman tilan tekstin', async () => {
    const screen = await renderWithLocalizationProvider(
      <StatusTeksti tilat={undefined} statusLocalized={'tuntematon'} />,
    );
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
  });
});
