'use client';
import { FormControl, FormControlLabel, FormGroup, FormLabel, InputLabel, MenuItem, Select, TextField } from '@mui/material';
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
        placeholder={'Hae sähköpostiosoitteella'}
        onChange={(e) => {
          handleTypedSearch(e.target.value);
        }}
        defaultValue={searchParams?.get('hakusana')?.toString()}/>
      <FormLabel>Tila</FormLabel>
      <Select
            id='tila'
            name='vastaanottotila'
            defaultValue={''}
            onChange={(e) => {
              router.push(pathname + '?' + createQueryString(e.target.name, e.target.value))
            }} 
            >
            <MenuItem value={''}>Kaikki</MenuItem>
            <MenuItem value={'epaonnistui'} disabled>Lähetys epäonnistui</MenuItem>
            <MenuItem value={'kesken'} disabled>Lähetys kesken</MenuItem>
        </Select>
    </FormControl>
  );
}
