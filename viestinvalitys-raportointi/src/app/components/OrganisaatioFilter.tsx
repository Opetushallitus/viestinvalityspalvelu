'use client';
/* eslint-disable @typescript-eslint/no-explicit-any */
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useDebouncedCallback } from 'use-debounce';
import { FormControl, FormLabel, TextField } from '@mui/material';

export default function OrganisaatioFilter( {handleChange}: {handleChange: any}) {

  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { replace } = useRouter();

  // päivitetään 3s viiveellä hakuparametrit
  const handleTypedSearch = useDebouncedCallback(term => {
    const params = new URLSearchParams(searchParams?.toString() || '');
    if (term) {
      params.set('orgSearchStr', term);
    } else {
      params.delete('orgSearchStr');
    }
    replace(`${pathname}?${params.toString()}`);
    handleChange
  }, 3000);


  return (
    <>
      <FormControl>
        <FormLabel>Hae organisaatiota</FormLabel>
        <TextField
          id="orgSearchStr"
          variant="outlined"
          placeholder={'Hae organisaatiota'}
          onChange={e => {
            handleTypedSearch(e.target.value);
          }}
          defaultValue={searchParams?.get('orgSearchStr')?.toString()}
        />
      </FormControl>
    </>
  );
}


