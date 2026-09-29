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
- Frontend Vue 3 + Vite + TypeScript em outro repositorio.
- Datas em `Instant`/UTC.
- Enums persistidos como `STRING`.
- Relacionamentos N:1 `LAZY` e obrigatorios quando aplicavel.
- DTOs na fronteira HTTP; entidades JPA nao serao expostas diretamente.

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
- Enum de perfil com `USUARIO`, `MODERADOR` e `ADMINISTRADOR`.
- Constraints unicas e FKs do nucleo academico.
- Teste de inicializacao validado contra PostgreSQL 17.

### Proxima etapa

Implementar `Material`, `Torrent` e `Magnet`.

Antes disso, devem ser confirmados os estados de `Material`. A proposta pendente e:

```text
PENDENTE
PUBLICADO
BLOQUEADO
REMOVIDO
```

`REMOVIDO` representa exclusao logica para preservar auditoria e relacionamentos futuros.

## Ordem futura

1. Completar entidades, relacionamentos e repositories.
2. Adicionar o populador de desenvolvimento.
3. Implementar cadastro, login, hash de senha, JWT e autorizacao.
4. Implementar CRUDs academicos.
5. Implementar materiais e categorias.
6. Implementar validacao, armazenamento e download de `.torrent`.
7. Implementar validacao e cadastro de Magnet URIs.
8. Implementar comentarios, avaliacoes, denuncias e moderacao.
9. Integrar o frontend.
10. Revisar seguranca, testes e deploy.
