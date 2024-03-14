import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useCallback } from 'react';

export default function useQueryParams() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  type UrlParam = {
    name: string;
    value: string;
  };

  const createQueryStrings = useCallback(
    (newparams: UrlParam[]) => {
      const params = new URLSearchParams(searchParams?.toString() || '');
      newparams.map(p => params.set(p.name, p.value));

      return params.toString();
    },
    [searchParams]
  );
  const createQueryString = useCallback(
    (name: string, value: string) => {
      const params = new URLSearchParams(searchParams);
      params.set(name, value);

      return params.toString();
    },
    [searchParams]
  );

  const setQueryParam = (queryName: string, value: string) => {
    router.push(`${pathname}?${createQueryString(queryName, value)}`);
  };

  return { queryParams: searchParams, createQueryString, createQueryStrings, setQueryParam };
}
