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

const HakukenttaSelect = ({
  value: selectedHakukentta,
  onChange,
}: {
  value: string;
  onChange: (e: SelectChangeEvent) => void;
}) => {
  return (
    <Select
      labelId="hakukentta-select-label"
      name="hakukentta-select"
      value={selectedHakukentta ?? ''}
      onChange={onChange}
      displayEmpty={true}
    >
      <MenuItem value="vastaanottaja" key="lahettaja">
        Vastaanottaja
      </MenuItem>
      <MenuItem value="lahettaja" key="lahettaja" disabled>
        Lähettäjä
      </MenuItem>
      <MenuItem value="viesti" key="lahettaja" disabled>
        Otsikko ja sisältö
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
  return (
    <FormControl sx={{ flex: '1 0 180px', textAlign: 'left' }}>
      <FormLabel id="hakukentta-select-label">Mistä haetaan</FormLabel>
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
      <HakukenttaInput value={selectedHakukentta || ''} onChange={(e) => setselectedHakukentta(e.target.value)}/>
      <FormControl
        sx={{
          flexGrow: 4,
          minWidth: '180px',
          textAlign: 'left',
        }}
      >
        <FormLabel htmlFor="haku-search">Hae viestejä</FormLabel>
        <OutlinedInput
          id="haku-search"
          name="haku-search"
          defaultValue={hakusana}
          onChange={(e) => {
            handleTypedSearch(e.target.value);
          }}
          autoFocus={true}
          type="text"
          placeholder="Hae hakuehdolla"
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
