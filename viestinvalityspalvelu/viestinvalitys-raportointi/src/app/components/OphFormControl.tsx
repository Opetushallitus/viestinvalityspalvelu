'use client';
import { FormControl, FormControlProps, FormHelperText, FormLabel, styled } from "@mui/material";
import { useId } from "react";

const StyledFormHelperText = styled(FormHelperText)(({ theme }) => ({
    margin: theme.spacing(0.5, 0),
  }));
  
  const EMPTY_ARRAY: Array<unknown> = [];

  export const OphFormControl = ({
    label,
    renderInput,
    helperText,
    errorMessages = EMPTY_ARRAY as Array<string>,
    ...props
  }: Omit<FormControlProps, 'children'> & {
    label: string;
    helperText?: string;
    errorMessages?: Array<string>;
    renderInput: (props: { labelId: string }) => React.ReactNode;
  }) => {
    const id = useId();
    const labelId = `OphFormControl-${id}-label`;
    return (
      <FormControl {...props}>
        <FormLabel id={labelId}>{label}</FormLabel>
        {helperText && (
          <StyledFormHelperText error={false}>{helperText}</StyledFormHelperText>
        )}
        {renderInput({ labelId })}
        {errorMessages.map((message, index) => (
          <StyledFormHelperText error={true} key={`${index}_${message}`}>
            {message}
          </StyledFormHelperText>
        ))}
      </FormControl>
    );
  };
  