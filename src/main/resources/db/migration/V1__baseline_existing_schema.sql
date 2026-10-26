-- Initial UnescShare schema reconstructed from the current PostgreSQL catalog.

CREATE TABLE usuarios (
    id uuid NOT NULL,
    data_cadastro timestamptz NOT NULL,
    email varchar(254) NOT NULL,
    nome varchar(120) NOT NULL,
    perfil varchar(20) NOT NULL,
    senha varchar(255) NOT NULL,
    CONSTRAINT ukkfsp0s1tflm1cwlj8idhqsad0 UNIQUE (email),
    CONSTRAINT usuarios_perfil_check CHECK (((perfil)::text = ANY ((ARRAY['USUARIO'::character varying, 'MODERADOR'::character varying, 'ADMINISTRADOR'::character varying])::text[]))),
    CONSTRAINT usuarios_pkey PRIMARY KEY (id)
);

CREATE TABLE instituicoes (
    id uuid NOT NULL,
    nome varchar(180) NOT NULL,
    sigla varchar(30) NOT NULL,
    CONSTRAINT instituicoes_pkey PRIMARY KEY (id),
    CONSTRAINT uknyhu5ueser7er70jknm20f08c UNIQUE (sigla),
    CONSTRAINT uktf2dvxilvsm54nwxx4vldps5j UNIQUE (nome)
);

CREATE TABLE categorias (
    id uuid NOT NULL,
    descricao varchar(500),
    nome varchar(80) NOT NULL,
    CONSTRAINT categorias_pkey PRIMARY KEY (id),
    CONSTRAINT uk9qte5svl2i6n82lpdyyheoi1h UNIQUE (nome)
);

CREATE TABLE cursos (
    id uuid NOT NULL,
    descricao varchar(1000),
    nome varchar(150) NOT NULL,
    instituicao_id uuid NOT NULL,
    CONSTRAINT cursos_pkey PRIMARY KEY (id),
    CONSTRAINT uk_curso_instituicao_nome UNIQUE (instituicao_id, nome)
);

CREATE TABLE disciplinas (
    id uuid NOT NULL,
    codigo varchar(30) NOT NULL,
    descricao varchar(1000),
    nome varchar(150) NOT NULL,
    curso_id uuid NOT NULL,
    CONSTRAINT disciplinas_pkey PRIMARY KEY (id),
    CONSTRAINT uk_disciplina_curso_codigo UNIQUE (curso_id, codigo)
);

CREATE TABLE materiais (
    id uuid NOT NULL,
    data_cadastro timestamptz NOT NULL,
    descricao varchar(4000) NOT NULL,
    status varchar(20) NOT NULL,
    titulo varchar(200) NOT NULL,
    visualizacoes bigint DEFAULT 0 NOT NULL,
    categoria_id uuid NOT NULL,
    disciplina_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    CONSTRAINT materiais_pkey PRIMARY KEY (id),
    CONSTRAINT materiais_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'PUBLICADO'::character varying, 'BLOQUEADO'::character varying, 'REMOVIDO'::character varying])::text[])))
);

CREATE TABLE torrents (
    id uuid NOT NULL,
    caminho_arquivo varchar(500) NOT NULL,
    data_cadastro timestamptz NOT NULL,
    info_hash varchar(128) NOT NULL,
    nome_arquivo varchar(255) NOT NULL,
    tamanho bigint NOT NULL,
    material_id uuid NOT NULL,
    CONSTRAINT torrents_pkey PRIMARY KEY (id),
    CONSTRAINT ukhag2ej1vo8snvirb1lv4b8r4x UNIQUE (info_hash)
);

CREATE TABLE magnets (
    id uuid NOT NULL,
    data_cadastro timestamptz NOT NULL,
    info_hash varchar(128) NOT NULL,
    magnet_uri varchar(4000) NOT NULL,
    material_id uuid NOT NULL,
    CONSTRAINT magnets_pkey PRIMARY KEY (id),
    CONSTRAINT ukgfsk3f7l580c85ai450a0n5fu UNIQUE (info_hash),
    CONSTRAINT ukkwo0kpr14vwstv16hy3cwppmx UNIQUE (magnet_uri)
);

CREATE TABLE comentarios (
    id uuid NOT NULL,
    conteudo varchar(4000) NOT NULL,
    data_cadastro timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    material_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    CONSTRAINT comentarios_pkey PRIMARY KEY (id),
    CONSTRAINT comentarios_status_check CHECK (((status)::text = ANY ((ARRAY['ATIVO'::character varying, 'OCULTO'::character varying, 'REMOVIDO'::character varying])::text[])))
);

CREATE TABLE avaliacoes (
    id uuid NOT NULL,
    data_cadastro timestamptz NOT NULL,
    nota numeric(3,2) NOT NULL,
    material_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    CONSTRAINT avaliacoes_pkey PRIMARY KEY (id),
    CONSTRAINT ck_avaliacao_nota CHECK (((nota >= 0.00) AND (nota <= 5.00))),
    CONSTRAINT uk_avaliacao_usuario_material UNIQUE (usuario_id, material_id)
);

CREATE TABLE denuncias (
    id uuid NOT NULL,
    data_cadastro timestamptz NOT NULL,
    descricao varchar(2000),
    motivo varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    material_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    CONSTRAINT denuncias_motivo_check CHECK (((motivo)::text = ANY ((ARRAY['DIREITOS_AUTORAIS'::character varying, 'CONTEUDO_INADEQUADO'::character varying, 'LINK_INVALIDO'::character varying, 'SPAM'::character varying, 'OUTRO'::character varying])::text[]))),
    CONSTRAINT denuncias_pkey PRIMARY KEY (id),
    CONSTRAINT denuncias_status_check CHECK (((status)::text = ANY ((ARRAY['PENDENTE'::character varying, 'EM_ANALISE'::character varying, 'PROCEDENTE'::character varying, 'IMPROCEDENTE'::character varying])::text[])))
);

CREATE TABLE moderacoes_comentarios (
    id uuid NOT NULL,
    data_cadastro timestamptz NOT NULL,
    estado_anterior varchar(20) NOT NULL,
    estado_novo varchar(20) NOT NULL,
    motivo varchar(1000) NOT NULL,
    comentario_id uuid NOT NULL,
    responsavel_id uuid NOT NULL,
    CONSTRAINT moderacoes_comentarios_estado_anterior_check CHECK (((estado_anterior)::text = ANY ((ARRAY['ATIVO'::character varying, 'OCULTO'::character varying, 'REMOVIDO'::character varying])::text[]))),
    CONSTRAINT moderacoes_comentarios_estado_novo_check CHECK (((estado_novo)::text = ANY ((ARRAY['ATIVO'::character varying, 'OCULTO'::character varying, 'REMOVIDO'::character varying])::text[]))),
    CONSTRAINT moderacoes_comentarios_pkey PRIMARY KEY (id)
);

CREATE TABLE moderacoes_denuncias (
    id uuid NOT NULL,
    data_cadastro timestamptz NOT NULL,
    estado_anterior varchar(20) NOT NULL,
    estado_novo varchar(20) NOT NULL,
    justificativa varchar(1000) NOT NULL,
    denuncia_id uuid NOT NULL,
    responsavel_id uuid NOT NULL,
    CONSTRAINT moderacoes_denuncias_estado_anterior_check CHECK (((estado_anterior)::text = ANY ((ARRAY['PENDENTE'::character varying, 'EM_ANALISE'::character varying, 'PROCEDENTE'::character varying, 'IMPROCEDENTE'::character varying])::text[]))),
    CONSTRAINT moderacoes_denuncias_estado_novo_check CHECK (((estado_novo)::text = ANY ((ARRAY['PENDENTE'::character varying, 'EM_ANALISE'::character varying, 'PROCEDENTE'::character varying, 'IMPROCEDENTE'::character varying])::text[]))),
    CONSTRAINT moderacoes_denuncias_pkey PRIMARY KEY (id)
);

ALTER TABLE cursos ADD CONSTRAINT fk_curso_instituicao FOREIGN KEY (instituicao_id) REFERENCES instituicoes(id);

ALTER TABLE disciplinas ADD CONSTRAINT fk_disciplina_curso FOREIGN KEY (curso_id) REFERENCES cursos(id);

ALTER TABLE materiais ADD CONSTRAINT fk_material_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id);

ALTER TABLE materiais ADD CONSTRAINT fk_material_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplinas(id);

ALTER TABLE materiais ADD CONSTRAINT fk_material_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);

ALTER TABLE torrents ADD CONSTRAINT fk_torrent_material FOREIGN KEY (material_id) REFERENCES materiais(id);

ALTER TABLE magnets ADD CONSTRAINT fk_magnet_material FOREIGN KEY (material_id) REFERENCES materiais(id);

ALTER TABLE comentarios ADD CONSTRAINT fk_comentario_material FOREIGN KEY (material_id) REFERENCES materiais(id);

ALTER TABLE comentarios ADD CONSTRAINT fk_comentario_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);

ALTER TABLE avaliacoes ADD CONSTRAINT fk_avaliacao_material FOREIGN KEY (material_id) REFERENCES materiais(id);

ALTER TABLE avaliacoes ADD CONSTRAINT fk_avaliacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);

ALTER TABLE denuncias ADD CONSTRAINT fk_denuncia_material FOREIGN KEY (material_id) REFERENCES materiais(id);

ALTER TABLE denuncias ADD CONSTRAINT fk_denuncia_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);

ALTER TABLE moderacoes_comentarios ADD CONSTRAINT fk_moderacao_comentario FOREIGN KEY (comentario_id) REFERENCES comentarios(id);

ALTER TABLE moderacoes_comentarios ADD CONSTRAINT fk_moderacao_comentario_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuarios(id);

ALTER TABLE moderacoes_denuncias ADD CONSTRAINT fk_moderacao_denuncia FOREIGN KEY (denuncia_id) REFERENCES denuncias(id);

ALTER TABLE moderacoes_denuncias ADD CONSTRAINT fk_moderacao_denuncia_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuarios(id);

CREATE INDEX idx_avaliacao_material ON avaliacoes USING btree (material_id);

CREATE INDEX idx_comentario_material ON comentarios USING btree (material_id);

CREATE INDEX idx_comentario_status ON comentarios USING btree (status);

CREATE INDEX idx_comentario_usuario ON comentarios USING btree (usuario_id);

CREATE INDEX idx_denuncia_material ON denuncias USING btree (material_id);

CREATE INDEX idx_denuncia_status ON denuncias USING btree (status);

CREATE INDEX idx_denuncia_usuario ON denuncias USING btree (usuario_id);

CREATE INDEX idx_magnet_material ON magnets USING btree (material_id);

CREATE INDEX idx_material_categoria ON materiais USING btree (categoria_id);

CREATE INDEX idx_material_disciplina ON materiais USING btree (disciplina_id);

CREATE INDEX idx_material_status ON materiais USING btree (status);

CREATE INDEX idx_material_usuario ON materiais USING btree (usuario_id);

CREATE INDEX idx_moderacao_comentario_data ON moderacoes_comentarios USING btree (comentario_id, data_cadastro, id);

CREATE INDEX idx_moderacao_comentario_responsavel ON moderacoes_comentarios USING btree (responsavel_id);

CREATE INDEX idx_moderacao_denuncia_data ON moderacoes_denuncias USING btree (denuncia_id, data_cadastro, id);

CREATE INDEX idx_moderacao_denuncia_responsavel ON moderacoes_denuncias USING btree (responsavel_id);

CREATE INDEX idx_torrent_material ON torrents USING btree (material_id);
