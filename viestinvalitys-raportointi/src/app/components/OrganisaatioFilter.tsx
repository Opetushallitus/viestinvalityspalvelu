'use client';
import { useDebouncedCallback } from 'use-debounce';
import {
  Box,
  FormControl,
  FormLabel,
  InputAdornment,
  OutlinedInput,
} from '@mui/material';
import { useQueryState } from 'nuqs';
import { NUQS_DEFAULT_OPTIONS } from '../lib/constants';
import { Search } from '@mui/icons-material';
import { useTranslation } from '../i18n/clientLocalization';

export default function OrganisaatioFilter() {
  const [organisaatioHaku, setOrganisaatioHaku] = useQueryState(
    'orgSearchStr',
    NUQS_DEFAULT_OPTIONS,
  );

  // päivitetään 3s viiveellä hakuparametri
  const handleTypedSearch = useDebouncedCallback((term) => {
    setOrganisaatioHaku(term);
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
        <FormLabel htmlFor="haku-search">{t('organisaatio.haku')}</FormLabel>
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
