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

Troque o valor de `POSTGRES_PASSWORD` no `.env` e inicie o PostgreSQL:

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

## Testes

Com o PostgreSQL local ativo e as variaveis do `.env` exportadas:

```bash
./mvnw test
```

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
