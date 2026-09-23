import React from 'react';
import { MaskedNumberInput, MaskedNumberInputProps } from './MaskedNumberInput';

type PercentInputProps = Omit<MaskedNumberInputProps, 'adornment'>;

/**
 * Percentual. O número é guardado como o usuário o lê — 110,00 significa 110%,
 * não 1,10 — porque é assim que assets.indexer_rate NUMERIC(10,4) o armazena.
 */
export const PercentInput = React.forwardRef<HTMLInputElement, PercentInputProps>(
  (props, ref) => <MaskedNumberInput ref={ref} adornment={{ text: '%', side: 'right' }} {...props} />
);

PercentInput.displayName = 'PercentInput';
