# UnescShare — Backend

API REST do UnescShare, uma plataforma para catalogar materiais academicos compartilhados por arquivos `.torrent` e Magnet URIs.

O backend armazena metadados, arquivos `.torrent` e referencias Magnet. Ele nao hospeda o conteudo academico distribuido pela rede BitTorrent.

## Requisitos

- Java 21
- Docker com Docker Compose

## Ambiente local

Crie o arquivo local de configuracao:

```bash
cp .env.example .env
```

Troque o valor de `POSTGRES_PASSWORD` no `.env`. Gere uma chave JWT exclusiva:

```bash
openssl rand -base64 32
```

Inclua o resultado em `JWT_SECRET` no `.env`, sem versionar ou compartilhar a chave.
O backend nao inicia sem uma chave valida. Para o primeiro incremento, use `JWT_TTL=15m`.
O `.env` existente nao foi alterado automaticamente; acrescente as variaveis JWT indicadas em `.env.example`.

Inicie o PostgreSQL:

```bash
docker compose up -d
docker compose ps
```

O Docker Compose le o `.env` automaticamente. Para disponibilizar as mesmas variaveis ao Spring Boot executado fora do Docker, exporte-as no terminal:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

A aplicacao usa o prefixo `/api/v1` para os endpoints REST.

## Autenticacao JWT

O primeiro incremento disponibiliza:

| Metodo | Endpoint | Acesso |
| --- | --- | --- |
| POST | `/api/v1/auth/cadastro` | Publico; cria apenas perfil `USUARIO` |
| POST | `/api/v1/auth/login` | Publico; retorna JWT Bearer |
| GET | `/api/v1/usuarios/me` | Token Bearer valido |

O token expira em 15 minutos por padrao, sem refresh token e sem revogacao imediata.
A validade e configuravel entre 1 e 30 minutos. A assinatura utiliza HS256 com chave Base64 de pelo menos 32 bytes aleatorios.
O servidor utiliza os recursos nativos do Spring Security para autenticacao de senha, emissao e validacao JWT e conversao de permissoes.
Nao ha filtro JWT proprio, login por formulario, autenticacao Basic ou sessao HTTP.

Consulte [docs/AUTENTICACAO.md](docs/AUTENTICACAO.md) para a estrutura dos arquivos, configuracao, exemplos de requisicoes, testes e limitacoes deste incremento.

## Dados iniciais de desenvolvimento

Ao iniciar com o perfil `dev`, um `CommandLineRunner` chama um servico transacional que cadastra:

- Instituicao `Universidade do Extremo Sul Catarinense`, com sigla `UNESC`.
- Curso `Ciência da Computação`, conforme a Matriz Curricular 6 de `docs/GRADE 6.pdf`.
- 59 disciplinas com codigos oficiais: 48 componentes semestrais (incluindo Optativa I e II) e 11 eletivas.
- Nove categorias: Livros, Apostilas, Resumos, Listas de exercícios, Provas, Apresentações, Cursos, Artigos e Outros materiais acadêmicos.

O catalogo fica em `src/main/resources/dados-iniciais/unesc-ciencia-computacao-matriz6.json`, em UTF-8, lido pelo Jackson configurado pelo Spring Boot e validado com Bean Validation.
O JSON preserva os nomes e rotulos NCI/NCC/NCA, codigos oficiais, semestres, creditos e horas do PDF.
Semestre, creditos e horas permanecem dados de referencia no arquivo, sem novas colunas JPA nesta etapa.
Nas eletivas, semestre e creditos sao nulos e as horas mantem o zero listado no documento, sem inferir obrigatoriedade ou equivalencia com as optativas.

O populador verifica instituicao pela sigla, curso pela instituicao/nome e disciplina pelo curso/codigo oficial.
Executa em uma unica transacao, preserva UUIDs e edicoes das disciplinas ja existentes e nao duplica registros na segunda inicializacao.
Somente o nome-placeholder `UNESC` e a descricao gerada da Matriz 5 (ou uma descricao nula do curso) sao atualizados para os metadados confirmados da Matriz 6; edicoes manuais diferentes sao preservadas.

O novo PDF nao traz ementas: novas disciplinas sao criadas com `descricao=null`, sem copiar as ementas da Matriz 5.
ENADE e atividades complementares nao recebem codigos inventados nem registros de disciplina.
O catalogo antigo foi removido dos recursos ativos. A remocao dos registros antigos do banco depende da execucao manual descrita em [docs/ATUALIZACAO_MATRIZ_6.md](docs/ATUALIZACAO_MATRIZ_6.md).
Se o curso ainda tiver codigos `TEMP-CC-*`, o runner interrompe a carga e solicita essa limpeza; nunca exclui registros automaticamente.

Para desativar a carga automatica, defina `SEED_ENABLED=false` no `.env` e exporte novamente as variaveis.
Fora do perfil `dev`, o runner nao e ativado. Usuarios e credenciais iniciais serao tratados na etapa de autenticacao.

## Testes

Com o PostgreSQL local ativo e as variaveis do `.env` exportadas:

```bash
./mvnw test
```

A suite inclui testes de catalogo da Matriz 6, carga idempotente, protecao contra mistura com a Matriz 5, preservacao de codigo/UUID/edicoes, notas decimais, unicidade de avaliacao, CHECK de intervalo via SQL, associacao de multiplos torrents/magnets e autenticacao JWT.
Os testes de integracao da carga devem ser executados apos concluir a limpeza dos registros da Matriz 5 no banco de desenvolvimento.
Os testes de integracao usam o perfil `test`, desativam o runner e os testes transacionais revertem seus dados ao finalizar cada transacao.
O teste de contexto tambem utiliza `test`, sem executar a carga inicial.
Uma chave exclusiva e publica de testes fica em `src/test/resources/application-test.properties`; ela nao faz parte do pacote de producao e nunca deve ser usada em desenvolvimento ou deploy.
O schema e gerenciado por `ddl-auto=update`, portanto os testes precisam de PostgreSQL de desenvolvimento disponivel e podem atualizar sua estrutura.

Ultima verificacao local em 06/10/2026: `./mvnw clean test` concluiu com 41 testes, sem falhas, erros ou testes ignorados. A carga da Matriz 6 criou 59 disciplinas e a segunda inicializacao criou zero. A configuracao JWT local foi aceita.
O agente pode executar Maven; comandos Docker devem ser executados pelo usuario.

## Encerrar o banco local

```bash
docker compose down
```

Os dados permanecem no volume `unescshare-postgres-data`. Para evitar perda acidental, a remocao desse volume deve ser feita somente de forma intencional.

## Estrutura de pacotes

O codigo e organizado por funcionalidade:

```text
br.com.murkyweb.unesc_share
├── config
├── security
├── shared
├── usuario
├── academico
├── categoria
├── material
├── compartilhamento
└── interacao
```

Cada funcionalidade recebera seus proprios controllers, services, repositories, DTOs e entidades conforme for implementada.

## Diretrizes de implementacao

Priorizar recursos nativos do Spring Security e Spring Web para reduzir codigo de infraestrutura proprio e facilitar a manutencao com base na documentacao oficial.
Customizacoes serao adicionadas apenas quando necessarias para as regras do UnescShare, com justificativa documentada e testes.
A autenticacao inicial utiliza JWT de curta duracao (15 minutos), assinatura HS256 e suporte nativo do Spring Security, sem refresh token ou revogacao imediata.
