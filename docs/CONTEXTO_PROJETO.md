# Contexto do projeto UnescShare

## Objetivo

O UnescShare e uma plataforma web para catalogacao, organizacao e descoberta de materiais academicos entre estudantes.

A hierarquia academica principal e:

```text
Instituicao
└── Curso
    └── Disciplina
        └── Material
```

Materiais podem representar livros, apostilas, resumos, listas de exercicios, provas, apresentacoes, cursos, artigos e outros conteudos academicos.

## Distribuicao dos conteudos

O servidor principal nao armazena o conteudo academico. A distribuicao ocorre pela rede BitTorrent por meio do cliente escolhido pelo usuario.

```text
UnescShare
├── Metadados academicos
├── Arquivos .torrent
└── Magnet URIs
         ↓
   Rede BitTorrent
         ↓
   Cliente do usuario
```

O sistema aceita exclusivamente:

- arquivos `.torrent`;
- Magnet URIs.

Nao deve existir upload direto de PDFs, EPUBs, videos, arquivos ZIP ou outros conteudos academicos.

## Modelo de dominio planejado

### Usuario

Campos: `id`, `nome`, `email`, `senha`, `perfil` e `dataCadastro`.

Relacionamentos 1:N com `Material`, `Comentario`, `Avaliacao` e `Denuncia`.

### Instituicao, Curso e Disciplina

- `Instituicao`: `id`, `nome` e `sigla`.
- `Curso`: `id`, `nome`, `descricao` e uma instituicao.
- `Disciplina`: `id`, `nome`, `descricao`, `codigo` e um curso.

Relações: `Instituicao 1:N Curso` e `Curso 1:N Disciplina`.

### Categoria

Campos: `id`, `nome` e `descricao`. Uma categoria classifica varios materiais.

### Material

Entidade central de catalogacao, com `id`, `titulo`, `descricao`, `dataCadastro`, `status` e `visualizacoes`.

Cada material pertence a um usuario, uma disciplina e uma categoria. Possui relacionamentos 1:N com `Torrent`, `Magnet`, `Comentario`, `Avaliacao` e `Denuncia`.

### Torrent

Entidade independente com `id`, `nomeArquivo`, `caminhoArquivo`, `infoHash`, `tamanho`, `dataCadastro` e um material.

### Magnet

Entidade independente com `id`, `magnetUri`, `infoHash`, `dataCadastro` e um material.

### Interacoes

- `Comentario`: conteudo, data de cadastro, status, usuario e material.
- `Avaliacao`: nota, data de cadastro, usuario e material. Deve existir apenas uma por usuario/material.
- `Denuncia`: motivo, descricao, status, data de cadastro, usuario e material.

## Arquitetura

```text
Vue 3 + TypeScript (repositorio separado)
                ↓ HTTP/REST
Spring Boot — /api/v1
├── Controllers
├── Services
├── Repositories
├── Security
└── JPA/Hibernate
                ↓
           PostgreSQL
```

O backend e organizado por funcionalidade nos pacotes `usuario`, `academico`, `categoria`, `material`, `compartilhamento` e `interacao`, alem dos pacotes transversais `config`, `security` e `shared`.

## Decisoes tecnicas confirmadas

- Java 21 e Spring Boot.
- PostgreSQL como banco relacional.
- UUID como identificador de todas as entidades.
- Hibernate com `ddl-auto=update` durante o prototipo.
- Flyway nao sera utilizado inicialmente.
- Dados iniciais serao inseridos por um `CommandLineRunner` idempotente no perfil `dev`.
- Docker Compose para o PostgreSQL local.
- Variaveis locais em `.env`, com `.env.example` versionado.
- API REST versionada sob `/api/v1`.
- Autenticacao via JWT de curta duracao, conforme escolha do usuario, sem refresh token neste incremento. Validade padrao de 15 minutos, configuravel de 1 a 30 minutos via `JWT_TTL`.
- Assinatura HS256 para o backend unico do prototipo, com chave aleatoria de pelo menos 32 bytes codificada em Base64 via `JWT_SECRET`. Sem chave padrao e sem geracao de chave a cada inicializacao; a configuracao invalida impede a inicializacao.
- Suporte nativo do Spring Security para autenticacao de senha, emissao e validacao JWT, sem filtros JWT proprios.
- Frontend Vue 3 + Vite + TypeScript em outro repositorio.
- Datas em `Instant`/UTC.
- Enums persistidos como `STRING`.
- Relacionamentos N:1 `LAZY` e obrigatorios quando aplicavel.
- DTOs na fronteira HTTP; entidades JPA nao serao expostas diretamente.
- Priorizar recursos nativos do Spring Security e Spring Web, reduzindo codigo de infraestrutura proprio e aproveitando as abstracoes e a documentacao oficial dos frameworks.
- Customizacoes devem atender necessidades concretas do projeto, com justificativa documentada e testes.
- Tokens enviados somente em `Authorization: Bearer`, API stateless sem sessao ou cookie de autenticacao, form login ou Basic. CSRF desativado nesse contexto; reavaliar caso cookies de autenticacao sejam introduzidos.
- Nao ha refresh token ou revogacao individual imediata. Logout e descarte do token no cliente; o token continua valido ate expirar. Mudancas de perfil so chegam aos tokens antigos apos expiracao e novo login.
- Cadastro publico sempre cria `USUARIO`, normaliza email e protege senha via BCrypt com identificador de algoritmo. Senha/hash nao sao retornados.
- DTOs, Bean Validation e `ResponseEntityExceptionHandler`/`ProblemDetail` para erros MVC. Erros de seguranca usam handlers Bearer nativos do Spring Security.

## Estado de implementacao

### Concluido

- Estrutura Maven/Spring Boot.
- Configuracao do PostgreSQL e Docker Compose.
- Configuracao por variaveis de ambiente.
- Dependencias JPA, validacao, web e seguranca.
- Constante do prefixo `/api/v1`.
- Estrutura de pacotes por funcionalidade.
- Base JPA com UUID.
- Entidades e repositories de Usuario, Instituicao, Curso, Disciplina e Categoria.
- Entidades e repositories de Material, Torrent e Magnet.
- Entidades e repositories de Comentario, Avaliacao e Denuncia.
- Enum de perfil com `USUARIO`, `MODERADOR` e `ADMINISTRADOR`.
- Enum de material com `PENDENTE`, `PUBLICADO`, `BLOQUEADO` e `REMOVIDO`.
- Avaliacoes decimais entre `0,00` e `5,00`, com duas casas e unicidade por usuario/material.
- Estados de comentario `ATIVO`, `OCULTO` e `REMOVIDO`.
- Enums de motivos e estados de moderacao das denuncias; fluxo HTTP ainda pendente.
- Constraints unicas e FKs do nucleo academico.
- Constraints, FKs e indices de materiais e mecanismos de compartilhamento.
- Teste de inicializacao validado contra PostgreSQL 17.
- Populador transacional e idempotente, executado em `dev` e configuravel via `SEED_ENABLED`.
- Catalogo ativo da Matriz 6: UNESC, Ciencia da Computacao, 59 disciplinas com codigos oficiais e nove categorias; carga confirmada no banco local pelo Maven, com zero novas disciplinas na segunda inicializacao.
- Testes de persistencia, idempotencia e preservacao de codigo oficial/UUID, validados contra PostgreSQL.
- Primeiro incremento da Etapa 3: cadastro publico, login JWT e consulta do usuario autenticado.
- CORS configurado por ambiente para o frontend separado, sem credenciais por cookie.
- Testes HTTP de cadastro, login, validade/assinatura/claims JWT, autorizacao por perfil, identidade do usuario e CORS.
- Perfil `test` com chave exclusiva para testes, sem runner automatico. Valores SQL nao sao registrados em `dev` para proteger hashes e dados pessoais.

### Catalogo academico inicial

A referencia ativa e `docs/GRADE 6.pdf`, Matriz Curricular 6, emitida em 13/12/2023, com validade declarada de 01/01/2022 a 01/01/2027.
O curso permanece `Ciência da Computação`; a instituicao usa o nome confirmado `Universidade do Extremo Sul Catarinense` e sigla `UNESC`.
O recurso UTF-8 `src/main/resources/dados-iniciais/unesc-ciencia-computacao-matriz6.json` contem 59 codigos oficiais: 48 componentes semestrais e 11 eletivas. Inclui Optativa I (27722) e Optativa II (27735) como componentes distintos das eletivas.
Os nomes preservam os rotulos NCI/NCC/NCA. Semestre, creditos e horas sao preservados no JSON de referencia, sem novos campos JPA.
Nas eletivas, semestre/creditos nao informados permanecem nulos e as horas mantem zero como listado no PDF. Nao presumir equivalencia com optativas.
O PDF nao traz ementas, portanto novas disciplinas tem `descricao=null`. ENADE e atividades complementares nao viram disciplinas com codigos inventados.
O populador usa curso/codigo oficial para idempotencia, preservando edicoes e UUIDs da Matriz 6. Atualiza somente placeholders reconhecidos de instituicao/curso.

O catalogo antigo da Matriz 5, com 44 codigos `TEMP-CC-*`, foi retirado dos recursos ativos. O PDF anterior permanece como referencia historica.
O usuario autorizou remover os registros antigos do banco, sem mapear equivalencias ou reutilizar ementas. Instituicao e curso devem preservar seus UUIDs.
Uma consulta fornecida pelo usuario confirmou os 44 UUIDs antigos no curso `78842494-b345-4da7-b483-72727863c327`, todos com zero materiais vinculados.
A limpeza e manual pelo script `docs/sql/remover-matriz5-local.sql`, com manifesto exato, verificacao de curso/codigo/vinculos e transacao. Nao faz parte do runner.
O usuario dispensou explicitamente o backup para esta substituicao no banco local descartavel. Isso nao autoriza limpar outras tabelas ou cursos.
A limpeza foi confirmada pela saida enviada pelo usuario: 44 disciplinas removidas, zero temporarias restantes e COMMIT, com instituicao e curso preservados. Nao houve backup, conforme dispensa expressa.
A carga foi confirmada em 06/10/2026: a primeira inicializacao pelo Maven criou 59 disciplinas e a segunda criou zero. A suite completa `./mvnw clean test` passou com 41 testes, sem falhas, erros ou testes ignorados. O backend iniciou com a configuracao JWT fornecida no `.env`, sem exibir os valores. Detalhes em `docs/ATUALIZACAO_MATRIZ_6.md`.
O agente pode executar comandos Maven, inclusive iniciar o backend e testar contra PostgreSQL; comandos Docker continuam exclusivos do usuario.

### Proxima etapa

A revisao da Matriz 6 foi concluida: limpeza manual pelo usuario, carga de 59 disciplinas, segunda inicializacao idempotente e 41 testes aprovados pelo Maven. O agente nao executou comandos Docker.
Detalhes, transcricao e comparacao estao em `docs/ANALISE_GRADE_6.md`; o procedimento operacional esta em `docs/ATUALIZACAO_MATRIZ_6.md`.

Confirmar a matriz de permissoes e implementar os CRUDs academicos incrementalmente, comecando por instituicoes.
O primeiro incremento de autenticacao esta documentado em `docs/AUTENTICACAO.md`.
O cadastro de usuario administrativo inicial ainda esta pendente; dependera de credenciais por ambiente e runner idempotente restrito a `dev`.
Rate limiting, recuperacao de senha, refresh token e revogacao individual nao fazem parte deste incremento e deverao ser discutidos em entregas futuras.

O estado `REMOVIDO` de `Material` representa exclusao logica para preservar auditoria e relacionamentos futuros.

## Ordem futura

1. Completar entidades, relacionamentos e repositories.
2. Adicionar o populador de desenvolvimento.
3. Implementar cadastro, login, hash de senha e autorizacao via JWT.
4. Implementar CRUDs academicos.
5. Implementar materiais e categorias.
6. Implementar validacao, armazenamento e download de `.torrent`.
7. Implementar validacao e cadastro de Magnet URIs.
8. Implementar comentarios, avaliacoes, denuncias e moderacao.
9. Integrar o frontend.
10. Revisar seguranca, testes e deploy.
