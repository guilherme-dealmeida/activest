-- Novas cores dos tipos de ativo (07/11/2026), efetivando o que foi ajustado direto na base.

UPDATE asset_types SET color_hex = '#FFDF00' WHERE name = 'TESOURO_DIRETO';
UPDATE asset_types SET color_hex = '#5C4033' WHERE name = 'FUNDO_INVESTIMENTO';
UPDATE asset_types SET color_hex = '#008080' WHERE name = 'STOCK';
UPDATE asset_types SET color_hex = '#A8E619' WHERE name = 'REIT';
