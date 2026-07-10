import { useEffect, useState } from 'react';
import { Box, DialogActions, InputAdornment, OutlinedInput } from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { LocalizationProvider, PickersActionBarProps } from '@mui/x-date-pickers';
import { AdapterMoment } from '@mui/x-date-pickers/AdapterMoment';
import 'moment/locale/fi';
import 'moment/locale/sv';
import { useDebouncedCallback } from 'use-debounce';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Search } from '@mui/icons-material';
import { LahettavaPalveluInput } from './LahettavaPalveluInput';
import { OphFormControl } from './OphFormControl';
import { OphButton, ophColors } from '@opetushallitus/oph-design-system';
import { SelectChangeEvent } from '@mui/material';
import moment from 'moment';
import { useHasChanged } from '../hooks/useHasChanged';
import i18n from '../i18n';

function CustomActionBar(props: PickersActionBarProps) {
  const { onAccept, onClear, className } = props;
  const { t } = useTranslation();
  return (
    <DialogActions className={className} sx={{ mb: 2, mr: 2 }}>
      <OphButton variant="contained" onClick={onAccept}>
        {t('yleinen.ok')}
      </OphButton>
      <OphButton variant="outlined" onClick={onClear}>
        {t('yleinen.tyhjenna')}
      </OphButton>
    </DialogActions>
  );
}

export default function Haku({ lahettavatPalvelut }: { lahettavatPalvelut: string[] }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [calendarErrors, setCalendarErrors] = useState<Array<string>>([]);
  const { t } = useTranslation();
  const locale = i18n.language || 'fi';

  const hakusana = searchParams.get('hakusana');
  const palvelu = searchParams.get('palvelu');
  const hakuAlkaen = searchParams.get('hakuAlkaen');
  const hakuPaattyen = searchParams.get('hakuPaattyen');

  const setParam = (key: string, value: string | null) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) {
        next.set(key, value);
      } else {
        next.delete(key);
      }
      next.delete('seuraavatAlkaen');
      return next;
    });
  };

  const hakusanaChanged = useHasChanged(hakusana);
  const palveluChanged = useHasChanged(palvelu);
  const hakuAlkaenChanged = useHasChanged(hakuAlkaen);
  const hakuPaattyenChanged = useHasChanged(hakuPaattyen);

  useEffect(() => {
    if (hakusanaChanged || palveluChanged || hakuAlkaenChanged || hakuPaattyenChanged) {
      setSearchParams((prev) => {
        const next = new URLSearchParams(prev);
        next.delete('seuraavatAlkaen');
        return next;
      });
    }
  }, [hakusanaChanged, palveluChanged, hakuAlkaenChanged, hakuPaattyenChanged]);

  const handleAlkuDateTimeChange = (value: moment.Moment | null) => {
    setParam('hakuAlkaen', value?.toISOString() ?? null);
  };

  const handleLoppuDateTimeChange = (value: moment.Moment | null) => {
    if (hakuAlkaen && value && !moment(hakuAlkaen).isBefore(value)) {
      setCalendarErrors([t('error.virheellinen-aikavali')]);
    } else {
      setCalendarErrors([]);
      setParam('hakuPaattyen', value?.toISOString() ?? null);
    }
  };

  const handleTypedSearch = useDebouncedCallback((term: string) => {
    if (term.length >= 5) {
      setParam('hakusana', term);
    } else if (term.length === 0) {
      setParam('hakusana', null);
    }
  }, 3000);

  const calendarSlotProps = {
    textField: { placeholder: t('yleinen.valitse'), error: false },
    calendarHeader: {
      sx: {
        '& .MuiIconButton-edgeEnd': {
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
        },
        '& .MuiIconButton-edgeStart': {
          position: 'absolute',
          right: 0,
          top: 0,
          bottom: 0,
        },
      },
    },
    leftArrowIcon: {
      sx: { border: '1px solid', borderRadius: '50%', color: ophColors.blue2 },
    },
    rightArrowIcon: {
      sx: { border: '1px solid', borderRadius: '50%' },
    },
  };

  return (
    <LocalizationProvider dateAdapter={AdapterMoment} adapterLocale={locale}>
      <Box>
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'row',
            justifyContent: 'stretch',
            gap: 2,
            marginBottom: 2,
            flexWrap: 'wrap',
            alignItems: 'flex-end',
          }}
        >
          <OphFormControl
            label={t('lahetykset.hae')}
            sx={{ flexGrow: 4, minWidth: '180px', textAlign: 'left' }}
            helperText={t('lahetykset.hakukentta-ohje')}
            renderInput={({ labelId }) => {
              return (
                <OutlinedInput
                  id="haku-search"
                  name="haku-search"
                  inputProps={{ 'aria-labelledby': labelId }}
                  defaultValue={hakusana}
                  key={hakusana}
                  onChange={(e) => {
                    handleTypedSearch(e.target.value);
                  }}
                  autoFocus={true}
                  placeholder={t('lahetykset.hakukentta-placeholder')}
                  type="text"
                  endAdornment={
                    <InputAdornment position="end">
                      <Search />
                    </InputAdornment>
                  }
                />
              );
            }}
          />
        </Box>
        <Box
          display="flex"
          flexDirection="row"
          justifyContent="space-between"
          gap={2}
          marginBottom={2}
          flexWrap="nowrap"
          alignItems="flex-end"
        >
          <LahettavaPalveluInput
            value={palvelu}
            onChange={(e: SelectChangeEvent) => setParam('palvelu', e.target.value || null)}
            palvelut={lahettavatPalvelut}
          />
          <OphFormControl
            label={t('lahetykset.haku.alkaen')}
            sx={{ textAlign: 'left', flexShrink: 2 }}
            renderInput={({ labelId }) => {
              return (
                <DateTimePicker
                  disableFuture={true}
                  value={moment(hakuAlkaen)}
                  onChange={(newValue) => handleAlkuDateTimeChange(newValue)}
                  aria-labelledby={labelId}
                  timeSteps={{ minutes: 1 }}
                  slots={{
                    actionBar: CustomActionBar,
                  }}
                  slotProps={calendarSlotProps}
                />
              );
            }}
          />
          <OphFormControl
            label={t('lahetykset.haku.paattyen')}
            sx={{ minWidth: '180px', textAlign: 'left' }}
            errorMessages={calendarErrors}
            renderInput={({ labelId }) => {
              return (
                <DateTimePicker
                  disableFuture={true}
                  value={moment(hakuPaattyen)}
                  onChange={(newValue) => handleLoppuDateTimeChange(newValue)}
                  aria-labelledby={labelId}
                  timeSteps={{ minutes: 1 }}
                  slots={{
                    actionBar: CustomActionBar,
                  }}
                  slotProps={calendarSlotProps}
                />
              );
            }}
          />
        </Box>
      </Box>
    </LocalizationProvider>
  );
}
