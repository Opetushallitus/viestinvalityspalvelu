'use client';
import { FormControl, FormControlLabel, FormGroup, FormLabel, InputLabel, MenuItem, Select, TextField } from '@mui/material';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useDebouncedCallback } from 'use-debounce';
import { useCallback } from 'react';
import useQueryParams from '@/app/hooks/useQueryParams';

export default function VastaanottajaHaku() {
  const { setQueryParam } = useQueryParams();
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const { replace } = useRouter();

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
        id='hakusana'
        variant='outlined'
        placeholder={'Hae sähköpostiosoitteella'}
        onChange={(e) => {
          handleTypedSearch(e.target.value);
        }}
        defaultValue={searchParams?.get('hakusana')?.toString()}/>
      <FormLabel>Tila</FormLabel>
      <Select
            id='tila'
            name='tila'
            defaultValue={''}
            onChange={(e) => {
              setQueryParam(e.target.name, e.target.value)
            }} 
            >
            <MenuItem value={''}>Kaikki</MenuItem>
            <MenuItem value={'epaonnistui'}>Lähetys epäonnistui</MenuItem>
            <MenuItem value={'kesken'}>Lähetys kesken</MenuItem>
        </Select>
    </FormControl>
  );
}
