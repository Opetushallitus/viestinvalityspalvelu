'use client';
import {
  Box,
  FormControl,
  FormLabel,
  InputAdornment,
  MenuItem,
  OutlinedInput,
  Select,
  SelectChangeEvent,
} from '@mui/material';
import { useDebouncedCallback } from 'use-debounce';
import { useQueryState } from 'nuqs';
import { Search } from '@mui/icons-material';
import { NUQS_DEFAULT_OPTIONS } from './lib/constants';
import { useTranslation } from './i18n/clientLocalization';

const HakukenttaSelect = ({
  value: selectedHakukentta,
  onChange,
}: {
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const { t } = useTranslation();
  return (
    <Select
      labelId="hakukentta-select-label"
      name="hakukentta-select"
      value={selectedHakukentta ?? ''}
      onChange={onChange}
      displayEmpty={true}
    >
      <MenuItem value="vastaanottaja" key="vastaanottaja">
        {t('lahetykset.haku.vastaanottaja')}
      </MenuItem>
      <MenuItem value="lahettaja" key="lahettaja" disabled>
      {t('lahetykset.haku.lahettaja')}
      </MenuItem>
      <MenuItem value="viesti" key="viesti" disabled>
      {t('lahetykset.haku.otsikko-sisalto')}
      </MenuItem>
    </Select>
  );
};

const HakukenttaInput = ({
  value,
  onChange,
}: {
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  const { t } = useTranslation();
  return (
    <FormControl sx={{ flex: '1 0 180px', textAlign: 'left' }}>
      <FormLabel id="hakukentta-select-label">{t('lahetykset.haku.mista-haetaan')}</FormLabel>
      <HakukenttaSelect value={value} onChange={onChange} />
    </FormControl>
  );
};

export default function Haku() {
  const [selectedHakukentta, setselectedHakukentta] = useQueryState("hakukentta", NUQS_DEFAULT_OPTIONS);
  const [hakusana, setHakusana] = useQueryState("hakusana", NUQS_DEFAULT_OPTIONS);

  // päivitetään 3s viiveellä hakuparametrit
  const handleTypedSearch = useDebouncedCallback((term) => {
    setHakusana(term)
  }, 3000);
  const { t } = useTranslation();
  return (
    <Box
      display="flex"
      flexDirection="row"
      justifyContent="stretch"
      gap={2}
      marginBottom={2}
      flexWrap="wrap"
      alignItems="flex-end"
    >
      <HakukenttaInput value={selectedHakukentta ?? ''} onChange={(e) => setselectedHakukentta(e.target.value)}/>
      <FormControl
        sx={{
          flexGrow: 4,
          minWidth: '180px',
          textAlign: 'left',
        }}
      >
        <FormLabel htmlFor="haku-search">{t('lahetykset.hae')}</FormLabel>
        <OutlinedInput
          id="haku-search"
          name="haku-search"
          defaultValue={hakusana}
          onChange={(e) => {
            handleTypedSearch(e.target.value);
          }}
          autoFocus={true}
          type="text"
          endAdornment={
            <InputAdornment position="end">
              <Search />
            </InputAdornment>
          }
        />
      </FormControl>
    </Box>
  );
}
