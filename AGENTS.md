# Instrucoes do projeto para agentes Codex

## Premissa central

O UnescShare e um catalogo academico baseado em compartilhamento BitTorrent. Nao e um servico tradicional de hospedagem de conteudos.

- Nunca implementar upload direto de PDF, EPUB, MP4, ZIP ou do conteudo academico.
- Aceitar como mecanismos de compartilhamento somente arquivos `.torrent` e Magnet URIs.
- `Material` representa a catalogacao e os metadados do conteudo.
- `Torrent` e `Magnet` sao entidades independentes e nao devem ser reduzidos a atributos de `Material`.
- Um `Material` pode possuir varios `Torrent` e varios `Magnet`.

## Stack e decisoes vigentes

- Java 21 e Spring Boot.
- API REST com prefixo `/api/v1`.
- Spring Data JPA/Hibernate.
- PostgreSQL.
- UUID em todas as entidades.
- `spring.jpa.hibernate.ddl-auto=update` durante o prototipo.
- Sem Flyway inicialmente.
- `CommandLineRunner` idempotente e restrito ao perfil `dev` para dados iniciais, quando as entidades necessarias estiverem prontas.
- PostgreSQL local via Docker Compose.
- Configuracao local via `.env`; nunca versionar o `.env` real.
- Frontend Vue 3, Vite e TypeScript mantido em outro repositorio.
- Organizacao Java por funcionalidade.
- Datas persistidas como `Instant`/UTC.
- Enums persistidos como texto.
- Relacionamentos JPA carregados como `LAZY` por padrao.
- Nao expor entidades JPA diretamente pela API; usar DTOs.
- Evitar `@Data` do Lombok em entidades.
- Evitar cascatas de remocao indiscriminadas.

## Forma de desenvolvimento

- Implementar incrementalmente, sem tentar concluir todo o sistema em uma unica etapa.
- Antes de fixar uma decisao arquitetural ainda indefinida, apresentar opcoes e impactos ao usuario.
- Preservar separacao entre controller, service, repository, DTO e entidade.
- Acompanhar mudancas com testes proporcionais ao risco.
- Manter `README.md` e `docs/CONTEXTO_PROJETO.md` atualizados quando uma decisao relevante mudar.

## Estado atual

A Etapa 1 e a Etapa 2.1 foram implementadas. Existem a configuracao inicial e as entidades/repositories de:

- Usuario;
- Instituicao;
- Curso;
- Disciplina;
- Categoria.

A proxima etapa planejada e a 2.2: `Material`, `Torrent` e `Magnet`. Os estados definitivos de `Material` ainda precisam ser aprovados pelo usuario.
