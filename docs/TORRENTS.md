# Arquivos .torrent — primeiro incremento

Frontend integrado em26/10/2026: adição de `.torrent` próprio com consulta de saldo e confirmação. **1110 testes frontend**,79 backend focados e2cenários opcionais upload/download SpringHTTP/PostgreSQL reais aprovados, com storage temporário e limpeza exclusiva de fixtures. Contrato da API inalterado; código/execução no [guia frontend](../../UNESC-Share-Frontend/docs/ADICAO_TORRENT_FRONTEND.md). Visual/DOM/FilePicker pendentes sem navegador.

## Escopo e decisoes

O usuario aprovou armazenamento local e a politica de permitir Torrent e Magnet com o mesmo Info Hash somente no mesmo Material. A premissa permanece: o servidor guarda metainfo `.torrent`, nao PDFs, EPUBs, videos, ZIPs ou o conteudo distribuido.

Esta entrega permite adicionar `.torrent` **v1** a Material existente. O incremento seguinte acrescentou cadastro inicial com um Torrent, sem Magnet, mantendo JSON Material+Magnets compativel; veja [CADASTRO_MATERIAL_TORRENT.md](CADASTRO_MATERIAL_TORRENT.md). Suporte v2/hibrido e edicao/remocao individual de mecanismos ficam para incrementos posteriores.

- Somente o autor adiciona, em PENDENTE/PUBLICADO. Equipe nao impersona autor; BLOQUEADO/REMOVIDO retornam 409.
- Upload bem-sucedido coloca o material em PENDENTE, exigindo publicacao pela equipe. Upload rejeitado nao altera estado/metadados/mecanismos anteriores.
- Um arquivo por requisicao, campo `arquivo`, sem outros arquivos/parametros. Ate 2 MiB por arquivo, requisicao multipart de ate 3 MiB, ate 10 Torrents por Material.
- `.torrent` deve estar no nome, mas extensao/MIME nao bastam: o corpo precisa passar pela validacao do metainfo. `application/octet-stream` tambem e aceito como MIME da parte; MIME declarado pelo cliente nao e uma prova.
- Arquivos nao sao expostos como recursos estaticos. UUIDs gerados pelo servidor identificam os nomes fisicos; caminho/nome interno do metainfo nunca determina onde gravar.
- Remocao logica de Material preserva os arquivos e registros para autor/equipe. Nao revoga copias nem retira conteudo da rede BitTorrent.

## Arquivos envolvidos

Caminhos relativos a raiz do repositorio. Os arquivos completos estao no workspace:

```text
src/main/java/br/com/murkyweb/unesc_share/
├── compartilhamento/
│   ├── service/InfoHashGuard.java
│   ├── torrent/
│   │   ├── controller/TorrentController.java
│   │   ├── dto/TorrentResponse.java
│   │   ├── repository/TorrentRepository.java
│   │   ├── service/TorrentParser.java
│   │   ├── service/TorrentService.java
│   │   └── storage/TorrentStorage.java
│   └── magnet/service/MagnetService.java
├── material/
│   ├── dto/MaterialDetalheResponse.java
│   └── service/MaterialQueryService.java
└── shared/api/ApiExceptionHandler.java
src/main/resources/application.properties
src/test/resources/application-test.properties
.env.example
.gitignore

src/test/java/br/com/murkyweb/unesc_share/
├── compartilhamento/torrent/
│   ├── TorrentFixtures.java
│   ├── TorrentParserTests.java
│   ├── TorrentStorageTests.java
│   ├── TorrentHttpTests.java
│   └── InfoHashGuardTests.java
├── compartilhamento/magnet/MagnetServiceTests.java
└── material/
    ├── TorrentApiTests.java
    └── TorrentTransacaoTests.java
```

Nao foram adicionadas dependencias, entidades, campos ou migrations. `Torrent` continua independente de `Material` e de `Magnet`. `caminhoArquivo` guarda apenas uma chave UUID.torrent relativa ao diretorio privado; `tamanho` e o numero de bytes do `.torrent`, nao o tamanho do conteudo academico.

O [multipart nativo do Spring Web](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html) resolve `MultipartFile`. O controller declara o tipo aceito; o service verifica autoria/estado, valida e coordena a persistencia; o storage isola o filesystem. Autenticacao/headers de seguranca continuam nativos do Spring Security, sem filtro JWT proprio.

O parser especifico existe porque Spring Web/Security nao decodificam metainfo BitTorrent. E um leitor limitado a catalogacao v1, acompanhado de testes, nao um cliente ou implementacao completa da rede.

## API

Todos os endpoints exigem Bearer JWT. Caminhos completos:

| Metodo | Endpoint | Resultado |
| --- | --- | --- |
| POST | `/api/v1/materiais/{materialId}/torrents` | Multipart, somente autor; 201 + Location do download |
| GET | `/api/v1/materiais/{materialId}/torrents/{torrentId}` | Download autenticado do metainfo |
| GET | `/api/v1/materiais/{materialId}` | Detalhe existente agora inclui `torrents` |

POST retorna apenas `id`, `nomeArquivo`, `infoHash`, `tamanho`, `dataCadastro`. O detalhe de Material acrescenta uma lista desses DTOs, mantendo Magnets. Nao retorna caminho de disco nem entidades JPA. Listagens resumidas nao carregam Torrents por item.

Download de PUBLICADO e permitido para autenticados; PENDENTE/BLOQUEADO/REMOVIDO somente para autor ou MODERADOR/ADMINISTRADOR, como nos detalhes. Terceiros recebem 404. Um torrent de outro material tambem retorna 404, mesmo que o solicitante conheca seu UUID.

Resposta de download usa `application/x-bittorrent`, Content-Length, `Cache-Control: no-store`, attachment com nome UUID.torrent e `X-Content-Type-Options: nosniff` do Spring Security. Revalida o metainfo e seu Info Hash antes de devolver, rejeitando corrupcao na identidade `info`. Nao comprova integridade de alteracoes feitas apenas em campos externos a `info`, como trackers; proteja o diretorio contra escrita de terceiros.

### Teste manual

Use um Material proprio e um `.torrent` v1 de conteudo autorizado. `TOKEN` e `MATERIAL_ID` abaixo sao variaveis do seu terminal; nao cole tokens/senhas no chat, no Git ou em logs. Nao habilite traces de HTTP/shell.

```bash
curl --fail-with-body \
  -H "Authorization: Bearer ${TOKEN}" \
  -F 'arquivo=@/caminho/absoluto/exemplo.torrent;type=application/x-bittorrent' \
  "http://localhost:8080/api/v1/materiais/${MATERIAL_ID}/torrents"

curl --fail-with-body \
  -H "Authorization: Bearer ${TOKEN}" \
  --output recebido.torrent \
  "http://localhost:8080/api/v1/materiais/${MATERIAL_ID}/torrents/${TORRENT_ID}"
```

Substitua o caminho e os UUIDs. O segundo comando grava somente o `.torrent` localmente e pode sobrescrever `recebido.torrent`; escolha um destino novo. JWT expira normalmente, sem refresh. Tokens usados na linha de comando podem aparecer na lista de processos do computador; para uso real prefira um cliente HTTP que proteja os segredos.

## Validacao e identidade

A [BEP 3](https://www.bittorrent.org/beps/bep_0003.html) define bencoding e o hash v1. Esta API calcula SHA-1 diretamente sobre o trecho original codificado de `info`; nunca calcula sobre o arquivo inteiro ou sobre JSON/re-encoding.

Valida dicionario raiz, chaves ordenadas por bytes sem duplicatas, codificacao canonica de strings/inteiros, consumo integral, `info` como dicionario, nome UTF-8, piece length positivo e hashes `pieces` coerentes com os comprimentos single/multi-file. Exige exatamente um de length/files. Caminhos precisam ser nao vazios, sem componentes de travessia/separadores/controles; duplicatas e conflito arquivo/diretorio sao rejeitados. Caminhos internos nunca sao abertos nem extraidos.

Limites: profundidade 32, 20 mil nos, chaves de ate 256 bytes, inteiros em long, ate 10 mil entradas de arquivo e ate 32 componentes por caminho. A contagem de pieces e a soma de comprimentos sao protegidas contra overflow. Metadados adicionais sao preservados no arquivo original, mas nao interpretados integralmente. Trackers, webseeds e disponibilidade/licenca/conteudo nao sao verificados ou acessados.

Torrents v2/hibridos (`meta version`/`file tree`) retornam 400 explicitamente; nao sao silenciosamente tratados como v1. O suporte a Magnet v2 existente permanece, mas isso nao implica suporte a arquivo v2. Uma futura entrega devera validar a arvore/camadas e definir representacao de hashes hibridos antes de habilita-los.

## Hash entre Torrent e Magnet

Hash e normalizado para hex minusculo. Torrent + Magnet podem ter o mesmo hash no mesmo Material; um segundo Torrent desse hash continua retornando 409, conforme a constraint existente. Hash de material bloqueado/removido continua reservado. Criar outro Material com Magnet cujo hash ja existe em Torrent retorna 409 e reverte o novo cadastro.

`InfoHashGuard` usa `JdbcTemplate` e o [advisory lock transacional do PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS). E necessario porque uma constraint em cada tabela nao impede conflitos entre tabelas, e nao existe linha para bloquear antes do primeiro cadastro de um hash. Locks sao obtidos em ordem de hash e liberados no fim da transacao. A chave do lock deriva de SHA-256; uma colisao na chave somente serializa hashes extras, nao os considera iguais.

A verificacao das duas tabelas ocorre apos o lock, na mesma transacao READ_COMMITTED; ambos os fluxos da API participam. O lock JPA do Material tambem serializa upload, edicao e moderacao. Nao ha nova tabela/trigger ou garantia para SQL externo que ignore esse protocolo. Conflitos legados nao sao corrigidos/excluidos automaticamente; nenhuma reconciliacao de registros reais foi executada.

## Armazenamento e transacao

Configuracao opcional no `.env` local:

```dotenv
TORRENT_STORAGE_DIR=./data/torrents
```

Sem a variavel, o padrao e `./data/torrents`, relativo ao diretorio de execucao. O `.env` real nao e alterado pelo agente. Escolha diretorio dedicado, privado, sem symlinks e fora de `static`, `public`, webroot ou compartilhamento publico. Somente a conta da aplicacao deve poder modifica-lo; o codigo nao protege contra um administrador local malicioso. Em POSIX, novos diretorios/arquivos sao criados com 0700/0600. ACLs de diretorios ja existentes sao responsabilidade operacional.

O caminho padrao esta ignorado no Git. Se escolher outro caminho dentro do repositorio, ignore-o tambem. Nao versione metainfo com dados privados. No deploy, preserve o diretorio junto do banco; persistir so o banco deixa referencias indisponiveis. O multipart nativo usa temporarios tecnicos do Servlet e limites de requisicao; arquivos rejeitados nao sao persistidos no storage do catalogo.

O storage cria chave UUID com CREATE_NEW, nao sobrescreve arquivos, rejeita symlinks/caminhos externos e limita tambem leitura/download. O arquivo e escrito antes da conclusao da transacao; uma [sincronizacao transacional nativa](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html) remove apenas o arquivo criado quando ocorre rollback, inclusive falha de constraint/commit. Falha de limpeza gera log fixo, sem caminhos/metainfo/segredos.

**Filesystem e PostgreSQL nao formam uma transacao distribuida.** Queda abrupta, resultado de commit desconhecido, falha de disco ou falha de limpeza podem deixar arquivo orfao ou referencia indisponivel. Nao ha rotina automatica de reconciliacao nesta entrega; nao excluir automaticamente arquivos com base numa consulta isolada. Arquivo ausente/corrompido ou caminho legado fora da chave segura retorna 503 sem revelar o caminho. Nenhum arquivo legado e movido/excluido silenciosamente.

## Erros

- 400: nome/campos/UUID/metainfo invalido, formato v2/hibrido nao suportado, multipart malformado ou arquivos extras.
- 401: JWT ausente/invalido/expirado.
- 404: material/torrent inexistente, torrent de outro material ou sem visibilidade/autoria.
- 409: hash duplicado/em outro material, limite de quantidade, estado proibido ou conflito de integridade.
- 413: limite de bytes do arquivo/requisicao excedido.
- 415: upload de Torrent com tipo HTTP diferente de multipart. POST de Material agora distingue JSON/multipart; PUT de metadados continua JSON-only.
- 503: storage indisponivel ou metainfo local ausente/invalido para download.

`ResponseEntityExceptionHandler` permanece responsavel por erros MVC, inclusive 413 nativo. O handler especifico de MultipartException cobre corpo malformado com mensagem fixa e nao substitui o tratamento mais especifico de excesso de tamanho. Handlers Bearer continuam nativos. Nao adicionar logs de corpo, headers Authorization, metainfo ou parametros privados de trackers.

## Executar e testar

Com PostgreSQL ja disponivel e bootstrap administrativo desativado:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Em outro terminal, carregue as mesmas variaveis silenciosamente e execute:

```bash
./mvnw -Dtest=TorrentParserTests,TorrentStorageTests,InfoHashGuardTests,TorrentHttpTests,TorrentApiTests,TorrentTransacaoTests,MagnetServiceTests test
./mvnw clean verify
```

Os fixtures de metainfo sao sinteticos e nunca consultam a rede BitTorrent. Testes de API usam PostgreSQL e JWT nativo, dados separados com rollback e diretorios temporarios. Cobrem visibilidade em quatro estados, perfis/autoria, hash cruzado nos dois sentidos, duplicates/limites, upload invalido, download e headers, ausencia/corrupcao/path traversal/symlink, rollback de registro/arquivo e falha apos insercao JPA. O teste de lock usa duas conexoes/transacoes reais e confirma espera/liberacao no rollback; nao e um teste end-to-end de corrida entre dois uploads HTTP com commit.

`TorrentHttpTests` inicia servidor em porta aleatoria de loopback, sem inserir dados: confirma 413 real do Servlet, 400 multipart sem boundary, roteamento de multipart pequeno e 415 de PDF direto. MockMvc sozinho nao exercita o parser/limite multipart do container. Hibernate continua com ddl-auto=update no prototipo; nao executar Docker pelo agente.

Rate limiting, quotas totais por usuario/disco, reconciliacao operacional, auditoria completa, controle otimista de versao e protecoes de infraestrutura continuam pendentes. SHA-1 aqui e identidade de protocolo v1, nunca hash de senha ou garantia de seguranca do conteudo. Antes de publicar, confirme origem, permissao e licenca dos materiais.

### Verificacao executada

Em 20/10/2026, `./mvnw clean verify` passou com **315 testes**, zero falhas/erros/ignorados e JAR executavel gerado. Sao 74 casos novos: 50 de parser, 5 de storage, 1 de lock real, 3 de HTTP real, 14 de API e 1 de falha transacional apos insercao. Os 241 anteriores tambem passaram. A primeira tentativa encontrou restricoes do sandbox e a seguinte estava sem o ambiente local; a verificacao final usou Maven autorizado e `.env` carregado silenciosamente.

O backend atualizado foi reiniciado em loopback:8080 com context-path `/api/v1`, bootstrap administrativo desativado e carga academica criando zero disciplinas. Nao foram criados materiais permanentes, nao houve consulta a trackers/conteudo ou comandos Docker, e o `.env` real permaneceu inalterado pelo agente. O JAR contem as classes de producao novas e nao contem as fixtures nem application-test.properties. `git diff --check` passou e o diretorio padrao esta ignorado no Git.
