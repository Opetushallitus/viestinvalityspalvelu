import { useQuery } from '@tanstack/react-query';
import { fetchAsiointikieli } from '../lib/data';
import { LanguageCode } from '../lib/types';

export const getAsiointiKieli = async (): Promise<LanguageCode> => {
  const data = await fetchAsiointikieli();
  return data ?? 'fi';
};

export const useAsiointiKieli = () =>
  useQuery({
    queryKey: ['getAsiointiKieli'],
    queryFn: getAsiointiKieli,

    staleTime: Infinity,
  });
