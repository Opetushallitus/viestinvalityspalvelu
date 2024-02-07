'use client';
import { FormControl, FormLabel, InputLabel, MenuItem, Select, TextField } from '@mui/material';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useDebouncedCallback } from 'use-debounce';
import { useCallback } from 'react';

export default function VastaanottajaHaku() {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const { replace } = useRouter();
  
  // lisätään hakuparametreihin uusi key-value-pari
  const createQueryString = useCallback(
    (name: string, value: any) => {
      const params = new URLSearchParams(searchParams?.toString() || '')
      params.set(name, value)
 
      return params.toString()
    },
    [searchParams]
  )

  // päivitetään 3s viiveellä hakuparametrit
  const handleTypedSearch = useDebouncedCallback((term) => {
    const params = new URLSearchParams(searchParams?.toString() || '');
    if (term) {
      params.set('hakusana', term);
    } else {
      params.delete('hakusana');
    }
    replace(`${pathname}?${params.toString()}`);
  }, 3000);
  
  return (
    <FormControl fullWidth>
      <FormLabel>Hae vastaanottajia</FormLabel>
      <TextField  
        id="hakusana"
        variant="outlined" 
        disabled
        placeholder={'Hae nimellä tai sähköpostiosoitteella'}
        onChange={(e) => {
          handleTypedSearch(e.target.value);
        }} 
        defaultValue={searchParams?.get('hakutermi')?.toString()}/>
    </FormControl>
  );
}
