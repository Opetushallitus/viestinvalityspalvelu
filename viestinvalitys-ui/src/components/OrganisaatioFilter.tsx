import { useDebouncedCallback } from 'use-debounce';
import { Box, FormControl, FormLabel, InputAdornment, OutlinedInput } from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { Search } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

export default function OrganisaatioFilter() {
  const [searchParams, setSearchParams] = useSearchParams();
  const organisaatioHaku = searchParams.get('organisaatioHaku');

  const handleTypedSearch = useDebouncedCallback((term: string) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (term) {
        next.set('organisaatioHaku', term);
      } else {
        next.delete('organisaatioHaku');
      }
      return next;
    });
  }, 3000);

  const { t } = useTranslation();
  return (
    <>
      <Box marginBottom={2}>
        <FormControl
          sx={{
            textAlign: 'left',
          }}
        >
          <FormLabel htmlFor="organisaatio-search">{t('organisaatio.haku')}</FormLabel>
          <OutlinedInput
            id="organisaatio-search"
            name="organisaatio-search"
            defaultValue={organisaatioHaku}
            onChange={(e) => {
              handleTypedSearch(e.target.value);
            }}
            autoFocus={true}
            type="text"
            placeholder={t('organisaatio.nimi')}
            endAdornment={
              <InputAdornment position="end">
                <Search />
              </InputAdornment>
            }
          />
        </FormControl>
      </Box>
    </>
  );
}
