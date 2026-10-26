# Migrações de banco com Flyway

O Flyway passa a ser a fonte de verdade para a evolução do schema PostgreSQL. O Hibernate fica em `spring.jpa.hibernate.ddl-auto=validate`: confere se as entidades correspondem ao schema e falha se houver divergência, sem criar ou alterar tabelas.

## Migração inicial

`src/main/resources/db/migration/V1__baseline_existing_schema.sql` cria, em um schema vazio, as 13 tabelas atuais, suas chaves, restrições e índices. Esta migração descreve estrutura; o `CommandLineRunner` de perfil `dev` continua responsável somente por dados iniciais de referência.

O teste `FlywayMigrationTests` executa V1 em um schema temporário com nome aleatório no PostgreSQL local, verifica versão e quantidade de tabelas e remove apenas esse schema de teste.

## Banco local que já contém as tabelas

Na primeira adoção do Flyway, não execute V1 contra um banco de desenvolvimento populado: a migração inicial tentaria criar tabelas que já existem. Faça um baseline único na versão 1. Isso cria a tabela `flyway_schema_history` e registra a versão existente sem executar V1 nem tocar nas tabelas/dados da aplicação.

No banco local deste projeto, esse baseline já foi registrado em 26/10/2026. O log confirmou versão 1 e a validação Hibernate passou. Não repita o procedimento nesse banco. O comando abaixo fica como referência para outra cópia local preexistente que ainda não tenha histórico Flyway.

No terminal do backend, com o PostgreSQL já iniciado pelo usuário e o `.env` local preenchido, execute uma vez:

```bash
set -a
source .env
set +a
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=1 ./mvnw spring-boot:run
```

Aguarde a aplicação iniciar e confirme a validação de entidades no log. Encerre com `Ctrl+C`. As duas propriedades de baseline valem somente para esse processo; não as grave no `.env` ou na configuração permanente. Em inicializações seguintes, rode normalmente `./mvnw spring-boot:run` depois de carregar o `.env`.

O Flyway não deve ser configurado permanentemente com `baseline-on-migrate=true`: isso reduz a proteção contra aplicar migrações em um banco existente sem revisão. Banco vazio recebe V1 normalmente. Se um banco não vazio não tiver `flyway_schema_history`, pare e determine explicitamente sua versão antes de inicializar.

> Não use `clean` contra o banco local. O baseline não apaga dados; ele registra a versão já existente. A tabela de histórico do Flyway é a única tabela de controle nova nessa adoção.

## Desenvolvimento a partir de agora

1. Não altere o schema de produção adicionando `ddl-auto=update`.
2. Para uma mudança estrutural, crie a próxima migração versionada em `src/main/resources/db/migration`, por exemplo `V2__descricao.sql`.
3. Não edite migrações já aplicadas a um banco compartilhado; acrescente uma nova versão.
4. Atualize entidades e migração na mesma alteração e valide com `./mvnw clean verify`.
5. O populador de dados iniciais segue idempotente e separado das migrações de schema.

A configuração local anterior pode conter `JPA_DDL_AUTO=update`; a aplicação agora usa `validate` fixo e ignora essa variável. Remova-a manualmente do `.env` quando desejar; o agente não altera o `.env` real.
