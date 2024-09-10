import { describe, expect, it, vi } from 'vitest';
import { VastaanotonTila } from '../lib/types';
import { LahetysStatus, StatusTeksti } from './LahetysStatus';
import { render, screen } from '@testing-library/react';
import { initI18nextForTests } from '../i18n/testLocalization';
import { I18nextProvider } from 'react-i18next';

// mockataan font loader koska design system teeman import LahetysStatus-komponentissa tuotti ongelmia
// ks. https://github.com/vercel/next.js/issues/59701
vi.mock('next/font/google', () => ({
  Open_Sans: () => ({
    style: {
      fontFamily: 'mocked',
    },
  }),
}))


describe('LahetysStatus', async () => {
  const i18next = await initI18nextForTests()
  it('näyttää keskeneräisen tilan', () => {
    render(
      <I18nextProvider i18n={i18next}>
      <LahetysStatus
        tilat={[
          { vastaanottotila: VastaanotonTila.SKANNAUS, vastaanottajaLkm: 2 },
          { vastaanottotila: VastaanotonTila.DELIVERY, vastaanottajaLkm: 3 },
        ]}
      />
      </I18nextProvider>
    );
    expect(screen.getByText('2/5 viestin lähetys kesken')).toBeInTheDocument();
    expect(screen.getByTestId('WatchLaterIcon')).toBeDefined();
  });
  it('näyttää epäonnistuneen tilan', () => {
    render(
      <I18nextProvider i18n={i18next}>
      <LahetysStatus
        tilat={[
          { vastaanottotila: VastaanotonTila.SKANNAUS, vastaanottajaLkm: 2 },
          { vastaanottotila: VastaanotonTila.REJECT, vastaanottajaLkm: 1 },
        ]}
      />,
      </I18nextProvider>
    );
    expect(screen.getByText('1/3 viestin lähetys epäonnistui')).toBeInTheDocument();
    expect(screen.getByTestId('ErrorIcon')).toBeDefined();
  });
  it('näyttää onnistuneen tilan', () => {
    render(
      <LahetysStatus
        tilat={[
          { vastaanottotila: VastaanotonTila.DELIVERY, vastaanottajaLkm: 2 },
        ]}
      />,
    );
    expect(screen.getByText('2/2 viestin lähetys onnistui')).toBeInTheDocument();
    expect(screen.getByTestId('CheckCircleIcon')).toBeDefined();
  });
  it('näyttää tuntemattoman tilan', () => {
    render(<LahetysStatus tilat={[]} />);
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
    expect(screen.getByTestId('WarningIcon')).toBeDefined();
  });
});
describe('StatusTeksti', () => {
  it('näyttää tyhjän tilan tekstin', () => {
    render(<StatusTeksti tilat={[]} statusLocalized={'tuntematon'} />);
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
  });
  it('näyttää tuntemattoman tilan tekstin', () => {
    render(<StatusTeksti tilat={undefined} statusLocalized={'tuntematon'} />);
    expect(screen.getByText(/ei viestejä/)).toBeInTheDocument();
  });
});
