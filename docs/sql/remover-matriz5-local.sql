-- Limpeza local autorizada pelo usuario, com backup expressamente dispensado nesta operacao.
-- Alvos: os 44 UUIDs/codigos retornados pelo usuario, no curso identificado.
-- Nao remove instituicao, curso, materiais, categorias ou disciplinas de outros cursos.
-- Nenhuma exclusao em cascata. A carga da Matriz 6 ocorre depois, pelo runner dev.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

CREATE TEMP TABLE _unescshare_matriz5_alvos (
    id UUID PRIMARY KEY,
    codigo TEXT NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO pg_temp._unescshare_matriz5_alvos (id, codigo) VALUES
    ('ea3d35a7-9340-458a-b4bd-a379e9d77851', 'TEMP-CC-001'),
    ('a6aa79ea-b3c2-439e-92fa-14a7c39e918c', 'TEMP-CC-002'),
    ('aa5c4a3a-af2f-4bbc-a948-5bae2408ae5a', 'TEMP-CC-003'),
    ('3f32ae8e-05cb-4059-91a0-c3cb27af9b07', 'TEMP-CC-004'),
    ('c5431049-f020-45c4-9ade-4669fb665b8c', 'TEMP-CC-005'),
    ('e30188db-6ac1-44c1-a721-c56ee0d39251', 'TEMP-CC-006'),
    ('9a4d7bfa-ea7a-4d38-9fc5-96e1b0f8977c', 'TEMP-CC-007'),
    ('ec7ccdd2-9a67-4dc7-928e-407f62291b9b', 'TEMP-CC-008'),
    ('a5d75eb7-8050-4022-8669-d2af964d447e', 'TEMP-CC-009'),
    ('0816a2f0-6c8d-40a5-a91b-59fe984f83ea', 'TEMP-CC-010'),
    ('c5f27560-392f-4d75-b1c4-ec6dc9465e16', 'TEMP-CC-011'),
    ('c8a41be4-a588-4c48-ba8c-7aaa1c31b27d', 'TEMP-CC-012'),
    ('459e5a0c-01a4-449a-8663-594c0002d4d1', 'TEMP-CC-013'),
    ('5cb63904-f747-4e81-af83-572204f8f9b3', 'TEMP-CC-014'),
    ('3ed92e44-d0d5-4e85-b5a7-1db388f9bf3b', 'TEMP-CC-015'),
    ('85ce8e7a-a0a7-47ef-b58b-3185fd944ccf', 'TEMP-CC-016'),
    ('acf80a1e-c295-4fea-84f9-44dd97ef4935', 'TEMP-CC-017'),
    ('3058525d-72b8-45e7-ab06-1e1dfbab9011', 'TEMP-CC-018'),
    ('6713e467-2097-4bdc-b650-b613e3d57602', 'TEMP-CC-019'),
    ('1dc8cafb-0e7b-4c67-8351-9d342dda0fbb', 'TEMP-CC-020'),
    ('bba3172e-3a14-4dfc-a948-a29633b75dc3', 'TEMP-CC-021'),
    ('94be0536-07e6-46a6-98cf-c51c55dcda10', 'TEMP-CC-022'),
    ('a61ca65e-3e30-4dfc-b2ba-e2b1cc01b729', 'TEMP-CC-023'),
    ('a5d789f0-b977-4cca-be53-0f84ec74af1b', 'TEMP-CC-024'),
    ('cddd174e-0960-470c-bba1-09ce61fc563a', 'TEMP-CC-025'),
    ('bbd932ef-5b4e-4b5f-9ee1-ea437cfe3970', 'TEMP-CC-026'),
    ('28f15bc0-160f-4955-87c3-bd9bff230cd5', 'TEMP-CC-027'),
    ('59c51742-fd39-45e8-af37-e4fef2c14fe6', 'TEMP-CC-028'),
    ('76fdd9e4-84d2-4c6a-a70b-355a4d5d1fcf', 'TEMP-CC-029'),
    ('90146451-09c1-4ea1-8b42-e66b41572b23', 'TEMP-CC-030'),
    ('012d66fb-a76a-4c54-83d7-70ff06076bfb', 'TEMP-CC-031'),
    ('83dcd911-55a3-4473-896d-ec6aa869af74', 'TEMP-CC-032'),
    ('b9323f63-21f2-4118-9d63-91d24f16c430', 'TEMP-CC-033'),
    ('0a34e382-333b-4a81-a0b7-5f37b7897c9c', 'TEMP-CC-034'),
    ('42f8f579-f15a-439a-8998-ce52a805d569', 'TEMP-CC-035'),
    ('6d7a82d5-f374-4e8a-941f-e493ed31cf9f', 'TEMP-CC-036'),
    ('b3e8b01d-09b7-49ed-8969-cf7b934281b5', 'TEMP-CC-037'),
    ('803f1c12-40b3-4755-abc2-e64f85102144', 'TEMP-CC-038'),
    ('47f21777-2707-4e88-9a29-0d0199d0b266', 'TEMP-CC-039'),
    ('fdbb4cd8-782d-4500-9125-5bcab50d26c1', 'TEMP-CC-040'),
    ('2dd62c56-65b6-4a20-a096-a377d76c58b1', 'TEMP-CC-041'),
    ('fdbb341b-d306-4844-92de-47ccf5ea3021', 'TEMP-CC-042'),
    ('3c08e755-e758-4e66-b1d1-7d756b366532', 'TEMP-CC-043'),
    ('881aed4e-86c1-4eac-a0fb-3dd3cb590a92', 'TEMP-CC-044');

DO $limpeza$
DECLARE
    curso_alvo CONSTANT UUID := '78842494-b345-4da7-b483-72727863c327';
    encontrados INTEGER;
    removidos INTEGER;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.cursos c
        JOIN public.instituicoes i ON i.id = c.instituicao_id
        WHERE c.id = curso_alvo
          AND c.nome = 'Ciência da Computação'
          AND UPPER(i.sigla) = 'UNESC'
    ) THEN
        RAISE EXCEPTION 'Curso/instituicao nao correspondem ao alvo aprovado. Nenhuma exclusao autorizada.';
    END IF;

    -- Trava apenas as linhas alvo; novas referencias FK concorrentes nao podem passar.
    PERFORM c.id FROM public.cursos c WHERE c.id = curso_alvo FOR UPDATE;
    PERFORM d.id FROM public.disciplinas d
    JOIN pg_temp._unescshare_matriz5_alvos a ON a.id = d.id
    FOR UPDATE OF d;

    SELECT COUNT(*) INTO encontrados
    FROM public.disciplinas d JOIN pg_temp._unescshare_matriz5_alvos a ON a.id = d.id;

    IF encontrados NOT IN (0, 44) THEN
        RAISE EXCEPTION 'Esperados 44 alvos ou nenhum em reexecucao; encontrados %. Operacao cancelada.', encontrados;
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.disciplinas d
        JOIN pg_temp._unescshare_matriz5_alvos a ON a.id = d.id
        WHERE d.curso_id <> curso_alvo OR d.codigo <> a.codigo
    ) THEN
        RAISE EXCEPTION 'Um UUID alvo mudou de curso/codigo. Operacao cancelada.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.disciplinas d
        WHERE d.curso_id = curso_alvo AND d.codigo LIKE 'TEMP-CC-%'
          AND NOT EXISTS (
              SELECT 1 FROM pg_temp._unescshare_matriz5_alvos a
              WHERE a.id = d.id AND a.codigo = d.codigo
          )
    ) THEN
        RAISE EXCEPTION 'Ha disciplinas temporarias fora do manifesto aprovado. Operacao cancelada.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.materiais m
        JOIN pg_temp._unescshare_matriz5_alvos a ON a.id = m.disciplina_id
    ) THEN
        RAISE EXCEPTION 'Ha materiais vinculados aos alvos. Nao sera realizada exclusao em cascata.';
    END IF;

    DELETE FROM public.disciplinas d
    USING pg_temp._unescshare_matriz5_alvos a
    WHERE d.id = a.id AND d.codigo = a.codigo AND d.curso_id = curso_alvo;

    GET DIAGNOSTICS removidos = ROW_COUNT;
    RAISE NOTICE 'Disciplinas da Matriz 5 removidas: %. Instituicao e curso preservados.', removidos;
END;
$limpeza$;

SELECT COUNT(*) AS temporarias_restantes
FROM public.disciplinas
WHERE curso_id = '78842494-b345-4da7-b483-72727863c327'
  AND codigo LIKE 'TEMP-CC-%';

COMMIT;
