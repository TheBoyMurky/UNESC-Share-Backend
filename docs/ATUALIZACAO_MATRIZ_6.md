# Substituicao do catalogo local pela Matriz 6

## Escopo aprovado

O usuario autorizou remover as 44 disciplinas antigas da Matriz 5 e manter apenas as disciplinas da Matriz 6.
A consulta fornecida por ele identificou o curso `78842494-b345-4da7-b483-72727863c327`, os 44 UUIDs/codigos temporarios e zero materiais vinculados a cada um.

O codigo ja foi atualizado. O usuario confirmou a limpeza: 44 disciplinas removidas, zero temporarias restantes e COMMIT, preservando instituicao e curso.
A carga e a verificacao foram concluidas pelo Maven em 06/10/2026; os comandos abaixo ficam registrados como procedimento, nao como uma nova pendencia.
O agente pode executar comandos Maven, mas nao executa comandos Docker; os comandos Docker deste documento sao destinados ao usuario.

## Resultado da verificacao local

- Limpeza manual confirmada pelo usuario: 44 disciplinas removidas, zero temporarias restantes e `COMMIT`.
- Primeira inicializacao com `dev` e carga habilitada: `Dados iniciais UNESC verificados: 59 disciplinas criadas.`
- Segunda inicializacao com a mesma configuracao: `Dados iniciais UNESC verificados: 0 disciplinas criadas.`
- Backend iniciado na porta 8080, com context-path `/api/v1` e configuracao JWT aceita.
- `./mvnw clean test`: 41 testes, zero falhas, zero erros, zero testes ignorados e `BUILD SUCCESS`.
- A verificacao usou PostgreSQL local; nenhum comando Docker foi executado pelo agente e os valores do `.env` nao foram exibidos.

- Preservar instituicao, curso, categorias e seus UUIDs.
- Excluir somente os 44 UUIDs/codigos aprovados; as novas disciplinas terao novos UUIDs.
- Nao migrar ementas ou inferir equivalencias entre as matrizes.
- Nao apagar materiais em cascata.
- Nao alterar dados de outros cursos.

## Arquivos envolvidos

- `src/main/resources/dados-iniciais/unesc-ciencia-computacao-matriz6.json`: 59 disciplinas oficiais, com dados de referencia do PDF.
- `src/main/java/br/com/murkyweb/unesc_share/config/seed/CatalogoMatriz6.java`: leitura com Jackson e validacao com Bean Validation e totais da fonte.
- `src/main/java/br/com/murkyweb/unesc_share/config/seed/DadosIniciaisService.java`: carga transacional por curso/codigo, sem exclusao automatica.
- `src/main/java/br/com/murkyweb/unesc_share/academico/disciplina/repository/DisciplinaRepository.java`: consultas por curso/codigo e verificacao de codigos temporarios.
- `src/test/java/br/com/murkyweb/unesc_share/config/seed/CatalogoMatriz6Tests.java` e `DadosIniciaisServiceUnitTests.java`: verificacoes sem banco.
- `src/test/java/br/com/murkyweb/unesc_share/config/seed/DadosIniciaisServiceTests.java`: integracao atualizada para a Matriz 6.
- `docs/sql/remover-matriz5-local.sql`: limpeza pontual com manifesto dos 44 UUIDs informados.

O antigo recurso TSV foi removido. O PDF da Matriz 5 permanece como referencia historica, nao como catalogo ativo.

## 1. Parar o backend

Execute os comandos na raiz do repositorio, com PostgreSQL ativo. Pare o backend anterior com Ctrl+C para impedir que a versao antiga do runner recrie as disciplinas temporarias.

O usuario dispensou explicitamente o backup nesta substituicao porque os dados locais sao descartaveis e podem ser recriados.
Essa dispensa se limita a esta operacao no banco de desenvolvimento; nao autoriza limpar todo o banco ou excluir dados de outros cursos.
Os registros antigos foram excluidos sem copia de restauracao. O novo catalogo pode ser recriado pelo runner, com novos UUIDs de disciplina.

## 2. Remover os registros antigos

Este comando realmente exclui as disciplinas antigas aprovadas:

```bash
docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -P pager=off' \
  < docs/sql/remover-matriz5-local.sql
```

O script:

- valida o curso e a instituicao;
- trava somente as linhas alvo e o curso durante a transacao;
- aceita os 44 alvos existentes, ou nenhum em uma reexecucao;
- rejeita UUIDs com curso/codigo diferente e registros temporarios fora do manifesto;
- verifica novamente os materiais vinculados;
- usa timeout e FKs existentes, sem cascatas;
- efetiva a exclusao somente ao concluir a transacao.

Resultado esperado na primeira execucao: aviso de 44 disciplinas removidas, `temporarias_restantes = 0` e `COMMIT`.
Em caso de erro, o comando para e a transacao nao e confirmada. Nao edite o manifesto nem amplie a exclusao para contornar a protecao; envie a saida para revisao.

A limpeza e a carga nova sao duas operacoes separadas. Entre elas, o curso permanecera sem as disciplinas antigas; o runner nao faz exclusoes e o script nao cadastra a Matriz 6.

## 3. Carregar a Matriz 6

Confira que o `.env` contem `JWT_SECRET` valido; nao ha chave padrao de desenvolvimento.

```bash
set -a
source .env
set +a
SPRING_PROFILES_ACTIVE=dev SEED_ENABLED=true ./mvnw spring-boot:run
```

O runner carrega 59 disciplinas: 48 componentes semestrais, incluindo Optativa I e II, e 11 eletivas.
Novas descricoes sao nulas porque o PDF nao contem ementas. O nome-placeholder da instituicao e a descricao gerada da Matriz 5 sao atualizados, sem trocar UUIDs de instituicao/curso.
Reiniciar deve informar zero disciplinas criadas. Edicoes manuais diferentes dos placeholders sao preservadas.
Se ainda houver `TEMP-CC-*` no curso, a carga sera interrompida, sem exclusao automatica nem mistura das matrizes.

## 4. Conferir o banco e executar os testes

```bash
docker compose exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -P pager=off' <<'SQL'
SELECT i.nome AS instituicao, c.id AS curso_id, c.descricao,
       COUNT(d.id) AS disciplinas,
       COUNT(d.id) FILTER (WHERE d.codigo LIKE 'TEMP-CC-%') AS temporarias,
       COUNT(DISTINCT d.codigo) AS codigos_distintos
FROM instituicoes i
JOIN cursos c ON c.instituicao_id = i.id
LEFT JOIN disciplinas d ON d.curso_id = c.id
WHERE c.id = '78842494-b345-4da7-b483-72727863c327'
GROUP BY i.nome, c.id, c.descricao;
SQL
```

Esperado: mesmo UUID do curso, instituicao com nome completo, descricao da Matriz 6, 59 disciplinas, 59 codigos distintos e zero temporarias.

Com as variaveis de conexao exportadas, apos a limpeza:

```bash
./mvnw test
```

Para validar apenas as regras sem PostgreSQL:

```bash
./mvnw -Dtest=CatalogoMatriz6Tests,DadosIniciaisServiceUnitTests,JwtConfigTests test
```

Os testes de integracao continuam usando PostgreSQL local, `ddl-auto=update` e rollback dos dados transacionais. Nao executar a suite completa antes da limpeza: os testes da carga tambem respeitam a protecao contra a mistura de matrizes.

## Proxima entrega

Limpeza, carga idempotente e testes confirmados. Retomar a definicao de permissoes e o CRUD de instituicoes, mantendo essa funcionalidade fora da substituicao de catalogo.
