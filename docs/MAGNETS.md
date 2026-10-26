# Adicao de Magnets a Material existente

Frontend integrado em26/10/2026: tela de adição própria com consulta de saldo, lote e confirmação do retorno a PENDENTE. **1077 testes frontend**,54 backend focados e2cenários opcionais SpringHTTP/PG reais aprovados; contrato deste guia permanece inalterado. Código e execução: [Adição de Magnets no frontend](../../UNESC-Share-Frontend/docs/ADICAO_MAGNETS_FRONTEND.md). Validação visual pendente sem navegador.

## Entrega e contrato

O autor agora pode adicionar um lote de 1..10 Magnet URIs ao proprio Material em PENDENTE/PUBLICADO, respeitando o limite acumulado de **10 Magnets por Material**. A escolha de lista preserva o formato `magnetUris` do cadastro inicial; enviar uma URI por chamada tambem funciona usando lista de um elemento.

```text
POST /api/v1/materiais/{materialId}/magnets
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "magnetUris": [
    "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Exemplo"
  ]
}
```

O hash e apenas exemplo de formato, nao garantia de conteudo disponivel/autorizado. A API tambem aceita application/*+json. O payload nao recebe autor, status ou metadados de Material; campos JSON extras sao ignorados e nao substituem essas regras.

Sucesso: **200 OK** com o `MaterialDetalheResponse` atualizado, incluindo todos os Magnets e Torrents existentes. A operacao em lote atualiza o Material e sua fila de revisao; nao anuncia Location de um recurso individual nem cria uma rota GET independente por Magnet. O detalhe de Material continua sendo o ponto de consulta.

- Autor de qualquer perfil pode adicionar; moderador/administrador nao impersonam o autor de terceiros.
- Sucesso coloca o Material em PENDENTE, inclusive se antes estava PUBLICADO. Conteudo novo exige nova revisao.
- BLOQUEADO/REMOVIDO nao aceitam adicao. Terceiros recebem 404 para requisicao valida; autor nesses estados recebe 409.
- Um lote e indivisivel: todas as URIs precisam ser validas, ineditas e caber no saldo. Nao ha truncamento, insercao parcial, substituicao ou reassociacao automatica.
- Segundo Magnet com o mesmo hash retorna 409 mesmo no mesmo Material, mudando trackers/dn ou usando outra representacao do hash. Torrent e Magnet podem compartilhar hash apenas no mesmo Material, conforme decisao anterior.
- Hash de outro Material, inclusive bloqueado/removido, permanece reservado. O endpoint nao serve para corrigir conflitos legados.
- UUIDs/datas/URIs dos mecanismos antigos, autor, titulo, descricao, classificacao, dataCadastro e visualizacoes sao preservados. Somente novas entidades Magnet e estado de revisao mudam.

Esta entrega nao implementa PUT/DELETE de mecanismos nem permite remover o ultimo. Os cadastros iniciais JSON com Magnets e multipart com Torrent continuam compativeis; Material segue sendo catalogacao, nao arquivo academico hospedado.

## Estrutura e implementacao

Arquivos completos no workspace, em caminhos relativos a raiz:

```text
src/main/java/br/com/murkyweb/unesc_share/
├── compartilhamento/magnet/
│   ├── controller/MagnetController.java
│   ├── dto/MagnetAdicaoRequest.java
│   ├── repository/MagnetRepository.java
│   └── service/MagnetService.java
└── material/service/MaterialService.java

src/test/java/br/com/murkyweb/unesc_share/
├── compartilhamento/magnet/MagnetServiceTests.java
└── material/
    ├── MaterialMagnetAdicaoApiTests.java
    └── MaterialTransacaoTests.java
```

Nao ha novas entidades, tabelas, campos, dependencias, migrations ou variaveis de ambiente. Nao ha alteracao do filesystem. O controller usa @RequestBody/@Valid/consumes do Spring Web e devolve DTO existente; autenticacao e tratamento de erros continuam nativos.

`MaterialService.adicionarMagnets` e o orquestrador transacional: obtem o lock JPA PESSIMISTIC_WRITE do Material, verifica autoria/estado, prepara o lote, confere o total acumulado, associa e altera o estado na mesma transacao. O lock existente tambem serializa contagem/adicao com outras mutacoes do Material. Sem @Version/ETag, a limitacao de revisao desatualizada da moderacao permanece.

`MagnetService.preparar(uris, materialId)` reutiliza o parser e o InfoHashGuard. A sobrecarga anterior `preparar(uris)` continua passando null para o cadastro de um novo Material, onde qualquer vinculo anterior e conflito. Na adicao, o UUID de destino e exclusivamente o Material autorizado pelo service, nao um campo fornecido no JSON.

O guard adquire advisory locks PostgreSQL ordenados por hash antes de consultar as duas tabelas. Constraints existentes continuam protegendo duplicidade dentro da tabela. Nao ha garantia para SQL externo que ignore normalizacao/locks; detalhes em [TORRENTS.md](TORRENTS.md). Preparacao/associacao exigem transacao existente e nao sao um bypass de autorizacao HTTP.

## Validacao, privacidade e limites

Cada URI tem ate 4000 caracteres. Lista obrigatoria, de 1 a 10 elementos nao nulos/brancos. A soma com os Magnets existentes nao pode passar de 10; exceder retorna409 sem alterar registros ou estado. Esse limite e separado do de 10 Torrents.

Parser existente: um xt v1 btih (40 hex ou 32 Base32) ou v2 btmh SHA-256; hashes canonicos em hex minusculo. Hibridos com varios xt continuam nao suportados. Parametros opcionais sao preservados, sem consultar trackers/webseeds/peers ou baixar metadados/conteudo. Referencia e detalhes em [MATERIAIS.md](MATERIAIS.md).

DTO de entrada tem toString redigido. Erros de parser/duplicidade nao ecoam URI, parametros privados ou SQL. Resposta autorizada continua incluindo a URI para copiar/abrir no frontend; redacao de logs nao significa remover dados da resposta. Nao inclua credenciais pessoais de trackers em URIs compartilhadas.

Validacao sintatica/identidade nao comprova disponibilidade, seguranca ou permissao de distribuicao. O backend nao abre o Magnet em cliente BitTorrent, nem implementa interface de copia; isso fica no frontend separado. Nao e aceito upload de conteudo academico.

## Erros

- 400: payload/lista/URI/UUID invalidos ou formato nao suportado.
- 401: JWT ausente/invalido/expirado.
- 404: Material inexistente ou nao pertencente ao solicitante, mesmo que ele seja administrador/moderador.
- 409: duplicidade no lote/tabela, hash de outro Material, limite acumulado ou estado proibido/conflito de integridade.
- 415: tipo HTTP nao JSON, como PDF direto ou multipart.

Bean Validation pode rejeitar um corpo invalido antes da verificacao de autoria. ProblemDetail/ResponseEntityExceptionHandler e handlers Bearer permanecem os existentes. Rate limiting, limites globais de bytes para JSON, quotas por usuario e auditoria completa continuam pendentes; nao considerar pronto para publicacao irrestrita em producao.

## Executar e testar

Com PostgreSQL local disponivel e bootstrap administrativo desativado:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Exemplo manual, definindo TOKEN e MATERIAL_ID no terminal (nao cole segredos no chat/Git):

```bash
curl --fail-with-body \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  --data '{"magnetUris":["magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Exemplo"]}' \
  "http://localhost:8080/api/v1/materiais/${MATERIAL_ID}/magnets"
```

Use um Material proprio e hashes de conteudo autorizado, nao os exemplos de formato para gerar registros reais. Tokens em linha de comando podem aparecer na lista de processos; prefira cliente HTTP que proteja segredos em uso real. Cada sucesso cria dados reais e pode retirar um Material publicado da busca ate nova revisao.

Testes automatizados, com ambiente carregado silenciosamente:

```bash
./mvnw -Dtest=MaterialMagnetAdicaoApiTests,MagnetServiceTests,MaterialTransacaoTests test
./mvnw clean verify
```

Usam PostgreSQL, JWT nativo e fixtures sinteticos com rollback. Cobrem autoria nos tres perfis/quatro estados, normalizacao v1/v2, hashes cruzados/duplicados, contagem acumulada, lotes atomicos, preservacao de metadados/mecanismos, payloads/tipos/UUIDs invalidos e ausencia de URI privada em toString/erros. Um teste injeta falha apos o flush de dois novos Magnets e verifica em outra transacao a ausencia deles apos rollback; as fixtures tambem sao revertidas.

O teste existente de advisory lock usa duas conexoes/transacoes reais, mas nao simula uma corrida end-to-end entre dois lotes HTTP. Nenhum tracker e acessado, nenhum arquivo de conteudo e baixado, nenhum dado real da Matriz6 e alterado e nenhum comando Docker e executado pelo agente.

Proximas entregas: definir politica de edicao/remocao de mecanismos (preservacao de auditoria e impedimento de remover o ultimo), ou iniciar interacoes/moderacao, conforme prioridade aprovada. Cadastro misto/lotes de arquivos, v2/hibridos em .torrent, reconciliacao operacional e protecao contra revisao desatualizada permanecem pendentes.

## Verificacao executada

Em 20/10/2026, `./mvnw clean verify` passou com **356 testes**, zero falhas/erros/ignorados e JAR executavel gerado. Sao 19 novos casos (17 de API, 1 de preparacao/contexto e 1 de rollback apos flush), alem dos 337 anteriores. Conjuntos selecionados tambem passaram antes da suite completa. `git diff --check` passou e o JAR contem controller/DTO novos, sem configuracao/classe de testes.

Backend atualizado reiniciado em localhost:8080, context-path `/api/v1`, bootstrap administrativo desativado e zero novas disciplinas criadas. A chamada local da nova rota sem JWT retornou401, sem criar registros. Nao foram solicitadas credenciais reais, nenhum material de teste permaneceu no banco, nenhum tracker/conteudo ou Docker foi acessado, e o `.env` real nao foi alterado pelo agente.
