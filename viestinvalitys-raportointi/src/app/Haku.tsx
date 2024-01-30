'use client';
import { FormControl, InputLabel, MenuItem, Select, TextField } from '@mui/material';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useDebouncedCallback } from 'use-debounce';
import { useCallback } from 'react';

export default function Haku() {
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
    console.log(`Searching... ${term}`);
    const params = new URLSearchParams(searchParams?.toString() || '');
    if (term) {
      params.set('hakusana', term);
    } else {
      params.delete('hakusana');
    }
    replace(`${pathname}?${params.toString()}`);
  }, 300);
  
  return (
    <FormControl fullWidth>
      <InputLabel id="haku-label">Mistä haetaan</InputLabel>
      <Select
        labelId="haku-label"
        id="hakuSelect"
        label="Mistä haetaan"
        defaultValue={''}
        onChange={(e) => {
          router.push(pathname + '?' + createQueryString(e.target.name, e.target.value))
        }} 
        >
        <MenuItem value={''}></MenuItem>
        <MenuItem value={'vastaanottaja'}>Vastaanottaja</MenuItem>
        <MenuItem value={'lahettaja'}>Lähettäjä</MenuItem>
        <MenuItem value={'viesti'}>Otsikko ja sisältö</MenuItem>
      </Select>
      <TextField  
        id="outlined-basic" label="Hae viestejä"
        variant="outlined" 
        placeholder={'Hae hakuehdolla'}
        onChange={(e) => {
          handleTypedSearch(e.target.value);
        }} 
        defaultValue={searchParams?.get('hakutermi')?.toString()}/>
    </FormControl>
  );
}
