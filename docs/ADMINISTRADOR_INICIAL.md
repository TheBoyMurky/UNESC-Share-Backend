# Administrador inicial de desenvolvimento

Este incremento permite preparar uma conta administrativa local para usar as operacoes de escrita de instituicoes. Nao adiciona endpoints de gerenciamento de usuarios, promocao de perfis ou recuperacao de senha e nao implementa o CRUD de cursos.

## Abordagem adotada

O contexto do projeto ja previa um `CommandLineRunner` idempotente, restrito a `dev`, com credenciais por ambiente.
Entre criar uma conta nova e promover uma existente, esta implementacao cria apenas uma conta nova: uma colisao com usuario comum ou moderador interrompe a carga, sem elevar privilegios nem trocar senhas.
O codigo fica disponivel, mas a ativacao exige uma escolha explicita em `ADMIN_SEED_ENABLED=true`. Nao existem nome, email ou senha padrao e o `.env` real nao e editado pelo agente.

| Situacao | Resultado |
| --- | --- |
| Perfil `dev` inativo | Runner e service de provisionamento nao sao registrados |
| `dev` ativo e flag ausente/false | Runner nao executa e credenciais nao sao exigidas |
| `dev` ativo, flag true e campos invalidos | Inicializacao interrompida com mensagem sem valores rejeitados |
| Email nao cadastrado | Cria `ADMINISTRADOR`, UUID, data UTC e senha com BCrypt |
| Email ja pertence a administrador | Preserva UUID, nome, senha/hash, perfil e data; nao duplica |
| Email pertence a usuario ou moderador | Inicializacao interrompida; conta preservada, sem promocao |
| Conflito de integridade na insercao | Transacao do usuario revertida; revisar a conta antes de reiniciar |

A busca usa email normalizado para minusculas e ignora diferencas de caixa. Nao se baseia em nome nem considera apenas a existencia de algum administrador: verifica a conta do email configurado.
O mecanismo nao limita o sistema a um administrador para sempre; sua finalidade e a preparacao explicita de contas locais de desenvolvimento.
Nao altera usuarios existentes nem serve para rotacionar senhas. Alterar a senha no ambiente nao modifica uma conta ja cadastrada.

## Estrutura dos arquivos

Os arquivos completos estao nos caminhos abaixo, relativos a raiz do repositorio:

```text
src/main/java/br/com/murkyweb/unesc_share/usuario/
├── config/AdministradorInicialProperties.java
├── config/AdministradorInicialRunner.java
└── service/AdministradorInicialService.java

src/main/resources/
├── application.properties
└── application-dev.properties

src/test/java/br/com/murkyweb/unesc_share/usuario/
├── AdministradorInicialServiceTests.java
├── AdministradorInicialConfigTests.java
└── AdministradorInicialIntegracaoTests.java

src/test/resources/application-test.properties
.env.example
```

- `AdministradorInicialProperties`: binding nativo de nome/email/senha, com `toString` redigido para nao expor credenciais.
- `AdministradorInicialRunner`: usa `@Profile`, `@ConditionalOnProperty` e `CommandLineRunner`, sem filtro ou endpoint proprio.
- `AdministradorInicialService`: transacao, validacao, verificacao do perfil existente e criacao com `PasswordEncoder` nativo.
- `CadastroRequest`: reutilizado para normalizar os dados e manter as mesmas regras de Bean Validation do cadastro publico, sem duplicar a politica de senha.
- Configuracao e service de provisionamento nao sao registrados fora de `dev`. O runner tambem exige flag true; nao depende do `SEED_ENABLED` do catalogo academico.

As condicoes usam [recursos nativos do Spring Boot](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/condition/ConditionalOnProperty.html), e as credenciais usam [configuracao externalizada](https://docs.spring.io/spring-boot/reference/features/external-config.html).
Nao foi alterada nenhuma entidade, tabela, constraint ou regra do cadastro publico: ele continua fixando `USUARIO`, mesmo que o JSON inclua outro perfil.

## Configuracao local

O PostgreSQL deve estar disponivel. Todos os comandos abaixo sao executados na raiz do repositorio; operacoes Docker permanecem sob responsabilidade do usuario.

Adicione ao seu `.env`, mantendo o arquivo fora do Git:

```dotenv
ADMIN_SEED_ENABLED=true
ADMIN_NAME='Seu nome'
ADMIN_EMAIL=''
ADMIN_PASSWORD=''
```

Preencha email e senha localmente antes de iniciar; vazios sao intencionais neste exemplo e nao permitem criar uma conta.
Use um email ainda nao cadastrado. Valores com espacos ou caracteres especiais precisam de aspas compativeis com o shell, pois o arquivo sera carregado com `source`.
Nao envie a senha no chat, nao use credenciais de outras contas e nao a passe como argumento de linha de comando.

As regras sao as mesmas do cadastro publico: nome nao vazio com ate 120 caracteres, email valido com ate 254 e senha entre 8 e 64 caracteres e no maximo 72 bytes UTF-8 para BCrypt.
Para a conta administrativa, prefira uma senha aleatoria e exclusiva. Uma opcao para gerar localmente 48 caracteres hexadecimais e:

```bash
openssl rand -hex 24
```

Guarde a senha em local seguro e preencha o `.env` local. Nao versione nem compartilhe o resultado.
As variaveis PostgreSQL/JWT ja utilizadas pelo projeto continuam necessarias.

Inicie o backend:

```bash
set -a
source .env
set +a
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Na primeira carga, deve aparecer:

```text
Administrador inicial criado. Desative ADMIN_SEED_ENABLED apos a configuracao local.
```

Em uma segunda carga habilitada para o mesmo administrador:

```text
Administrador inicial ja existente; nenhuma credencial ou perfil foi alterado.
```

Nenhuma dessas mensagens inclui nome, email, senha, hash ou token.
Se o email pertencer a uma conta sem perfil administrativo, escolha outro email ainda nao cadastrado ou desative a carga. Nao apague nem promova contas para contornar essa protecao.

### Depois da primeira carga

Pare o backend, altere `ADMIN_SEED_ENABLED=false` no `.env` e remova as credenciais administrativas do arquivo. Em um terminal que ja as tenha exportado:

```bash
unset ADMIN_NAME ADMIN_EMAIL ADMIN_PASSWORD
set -a
source .env
set +a
./mvnw spring-boot:run
```

Isso evita deixar a senha de bootstrap no ambiente de inicializacoes futuras; a conta e seu hash permanecem no banco.
Desativar o runner nao remove a conta nem troca sua senha. Sem o runner habilitado, as credenciais de bootstrap deixam de ser necessarias.

## Conferir o acesso

Use o login normal `POST /api/v1/auth/login` com email/senha locais, conforme [AUTENTICACAO.md](AUTENTICACAO.md), preferencialmente por um cliente HTTP que mantenha os valores fora do historico do terminal.
Com o token retornado, `GET /api/v1/usuarios/me` deve informar `perfil: ADMINISTRADOR`.
As operacoes administrativas de instituicoes estao descritas em [INSTITUICOES.md](INSTITUICOES.md).
Nao existe endpoint alternativo de login administrativo; assinatura, expiracao e validacao do JWT sao as mesmas de qualquer usuario.

## Seguranca e limites

- O service usa o `PasswordEncoder` compartilhado, que gera BCrypt com identificador `{bcrypt}`; nunca persiste a senha em texto puro.
- A validacao e executada pelo `Validator` nativo do Jakarta no service e a mensagem de falha e fixa. Nao usamos validacao automatica das propriedades com valores rejeitados no diagnostico de binding, pois esse diagnostico poderia revelar a senha de inicializacao.
- `toString` das propriedades e do DTO nao expõe credenciais. Nao registrar objetos de usuario, propriedades, excecoes SQL completas ou valores recebidos.
- O log automatico `org.hibernate.orm.jdbc.error` fica desativado: detalhes de constraints JDBC podem incluir valores de colunas, mesmo com logging de bind desligado. As falhas esperadas da carga usam mensagens sanitizadas; esse ajuste reduz detalhes automaticos de diagnostico do Hibernate.
- O `saveAndFlush` acontece dentro da transacao. Um conflito nao e tratado com nova consulta na mesma transacao potencialmente abortada; a inicializacao falha e o operador pode revisar o caso antes de reiniciar.
- Cada runner usa sua propria transacao: uma falha no administrador nao reverte retroativamente uma carga academica que outro runner ja tenha concluido.
- Nao ha endpoint para promocao, reset de senha ou ativacao dessa rotina. Nunca habilitar `dev` em deploy publico; esta rotina nao e o provisionamento de producao.
- Tokens emitidos seguem a duracao curta existente, sem refresh token e sem revogacao imediata.

## Testes

Com as variaveis de conexao exportadas e PostgreSQL local ativo:

```bash
./mvnw -Dtest=AdministradorInicialServiceTests,AdministradorInicialConfigTests,AdministradorInicialIntegracaoTests test
./mvnw clean verify
```

Os testes de configuracao isolam os beans para conferir perfil/flag e executar o runner sem banco. Tambem verificam que as mensagens de sucesso nao exibem as credenciais de teste.
Os testes de integracao ativam `dev` e `test` apenas para registrar o service, mas desativam explicitamente os dois runners automaticos. Chamam o provisionamento dentro de transacoes revertidas, verificam BCrypt, preservacao de UUID/hash/data, bloqueio de promocao e login com acesso ao CRUD administrativo.
As credenciais desses testes sao ficticias e nao persistem apos o rollback. O schema ainda pode ser atualizado por `ddl-auto=update`.
Os testes automatizados nao criam administrador persistente. A criacao real depende de ativacao explicita e das credenciais locais; o agente nao preenche nem altera o `.env` real.

Verificacao em 20/10/2026: 17 testes novos aprovados isoladamente e `./mvnw clean verify` com 79 testes no total, zero falhas, erros ou testes ignorados, e JAR executavel gerado.

### Validacao real autorizada

Ainda em 20/10/2026, apos o usuario preencher as variaveis e pedir a execucao, o backend foi iniciado pelo Maven com `dev`, restrito a `127.0.0.1:8080`.
O runner confirmou `Administrador inicial criado.` e a carga academica informou zero novas disciplinas.
Foram conferidos via HTTP, sem exibir credenciais, identidade ou token:

- `/usuarios/me` sem token: `401`.
- Login com as credenciais configuradas: `200`.
- `/usuarios/me` autenticado: `200`, perfil `ADMINISTRADOR` e DTO sem senha/hash.
- Listagem de instituicoes autenticada: `200`.
- POST de instituicao com corpo vazio: `400`, passando pela autorizacao administrativa e falhando na validacao, sem inserir dados.

Nenhuma instituicao foi criada, editada ou excluida nessa verificacao. O administrador real permanece cadastrado; o `.env` nao foi alterado pelo agente e nenhum comando Docker foi executado.
O servidor foi mantido ativo ao concluir a verificacao. A flag e as credenciais locais ainda precisam ser removidas/desativadas pelo usuario antes de futuras inicializacoes, conforme a secao "Depois da primeira carga".

O proximo incremento e o CRUD de cursos, vinculado a instituicoes e com as mesmas permissoes academicas aprovadas.
