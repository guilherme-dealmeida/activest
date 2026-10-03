-- strategies.include_reservation sai.
--
-- A coluna deixava o ideal de um tipo "incluir a reserva" no rebalanceamento
-- por tipo. Nunca valeu true: o unico lugar que a gravava era o Salvar da aba
-- Ideal Estrategico, que mandava false fixo, e o PUT e full replace.
--
-- Com a estrategia de reserva (V11), a reserva de emergencia passa a ser um
-- bloco proprio que concorre com o resto da carteira. Uma flag que a somasse
-- de volta num tipo contaria o mesmo CDB duas vezes: uma na tabela da reserva
-- e outra na de Renda Fixa. A exclusao da reserva no rebalanceamento por tipo
-- passa a ser incondicional, no codigo.
--
-- Guarda: se alguma linha tiver true, alguem gravou por fora e o valor tem
-- dono. Melhor falhar a subida do que apagar em silencio.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM strategies WHERE include_reservation) THEN
        RAISE EXCEPTION 'strategies.include_reservation tem linhas com true; revise antes de derrubar a coluna';
    END IF;
END
$$;

ALTER TABLE strategies DROP COLUMN include_reservation;
