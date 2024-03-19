import { VastaanotonTila } from "../lib/types";
import { LahetysStatus } from "./LahetysStatus";
import { render, screen } from '@testing-library/react'

describe('LahetysStatus', () => {
    it('näyttää keskeneräisen tilan', () => {
      render(<LahetysStatus tilat={[{vastaanottotila: VastaanotonTila.SKANNAUS,vastaanottajaLkm: 2},{vastaanottotila: VastaanotonTila.DELIVERY,vastaanottajaLkm: 2}]} />)      
      expect(screen.getByText(/kesken/i)).toBeInTheDocument()
      expect(screen.getByTestId('WatchLaterIcon')).toBeDefined()
    })
    it('näyttää epäonnistuneen tilan', () => {
      render(<LahetysStatus tilat={[{vastaanottotila: VastaanotonTila.SKANNAUS,vastaanottajaLkm: 2},{vastaanottotila: VastaanotonTila.REJECT,vastaanottajaLkm: 2}]} />)      
      expect(screen.getByText(/lähetys epäonnistui/i)).toBeInTheDocument()
      expect(screen.getByTestId('ErrorIcon')).toBeDefined()
    })
    it('näyttää onnistuneen tilan', () => {
      render(<LahetysStatus tilat={[{vastaanottotila: VastaanotonTila.DELIVERY,vastaanottajaLkm: 2}]} />)      
      expect(screen.getByText(/lähetys onnistui/i)).toBeInTheDocument()
      expect(screen.getByTestId('CheckCircleIcon')).toBeDefined()
    })
    it('näyttää tuntemattoman tilan', () => {
      render(<LahetysStatus tilat={[]} />)      
      expect(screen.getByText(/ei viestejä/)).toBeInTheDocument()
      expect(screen.getByTestId('WarningIcon')).toBeDefined()
    })
  })