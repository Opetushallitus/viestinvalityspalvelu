'use client';
import { FormControl, FormLabel, MenuItem, Select, TextField } from '@mui/material';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useDebouncedCallback } from 'use-debounce';
import useQueryParams from './hooks/useQueryParams';

export default function Haku() {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const { setQueryParam } = useQueryParams();
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
      <FormLabel>Mistä haetaan</FormLabel>
      <Select
        id='hakuvalikko'
        name='hakukentta'
        defaultValue={''}
        onChange={(e) => {
          setQueryParam(e.target.name, e.target.value)
        }} 
        >
        <MenuItem value={''}></MenuItem>
        <MenuItem value={'vastaanottaja'}>Vastaanottaja</MenuItem>
        <MenuItem value={'lahettaja'} disabled>Lähettäjä</MenuItem>
        <MenuItem value={'viesti'} disabled>Otsikko ja sisältö</MenuItem>
      </Select>
      <FormLabel>Hae viestejä</FormLabel>
      <TextField  
        id="hakusana"
        variant="outlined" 
        placeholder={'Hae hakuehdolla'}
        onChange={(e) => {
          handleTypedSearch(e.target.value);
        }} 
        defaultValue={searchParams?.get('hakusana')?.toString()}/>
    </FormControl>
  );
}
