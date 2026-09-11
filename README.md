# TriAIge / triaige-srv-notification

> Serviço de notificação por e-mail: consome o resultado final da triagem e envia o relatório completo aos destinatários cadastrados do escritório.

## Sobre o projeto

O **TriAIge** é uma plataforma de triagem jurídica assistida por IA: escritórios de advocacia enviam os documentos de um caso e recebem de volta um relatório estruturado, com o texto sensível anonimizado antes de qualquer processamento por IA.

Este repositório contém o serviço responsável por:

- Consumir o evento de "resultado pronto" publicado pelo `triaige-srv-orchestrator` ao final da análise de IA.
- Localizar os destinatários cadastrados da sessão e enviar um e-mail com o relatório de triagem completo (buscado do S3) no corpo.
- Controlar retry/DLQ por destinatário em caso de falha de envio, e reportar o resultado final ao Orchestrator.

## Papel deste serviço na arquitetura

```text
triaige-srv-orchestrator
   ↓ (Q3: resultado pronto)
triaige-srv-notification
   ├── busca o relatório completo no S3 (bucket curated)
   ├── envia e-mail por destinatário cadastrado (SMTP Gmail)
   ├── falha → agenda retry (fila própria) até esgotar tentativas → DLQ
   └── reporta o resultado final (callback HTTP) ──→ triaige-srv-orchestrator
```

## Responsabilidades

Este serviço é responsável por:

- Consumir a fila de resultados (`triaige-results-ready`, Q3) publicada pelo Orchestrator.
- Buscar o relatório de triagem (Markdown) já anonimizado no S3 e renderizá-lo como HTML para o corpo do e-mail.
- Resolver os destinatários cadastrados da sessão (com fallback para o cadastro de contatos do escritório) e enviar um e-mail por destinatário via SMTP.
- Registrar cada tentativa de envio (`notification_deliveries`) com controle de concorrência (claim atômico) para nunca duplicar um envio já em andamento.
- Reagendar falhas retentáveis em uma fila própria de retry, com backoff crescente, e mover para DLQ ao esgotar as tentativas.
- Reportar de volta ao Orchestrator (callback HTTP) o resultado final da notificação da sessão (sucesso, falha parcial ou falha total).
- Auditoria e métricas de todo o fluxo.

### Fora do escopo

Este serviço não é responsável por:

- Gerar ou renderizar o relatório de triagem — isso é feito pelo `triaige-srv-orchestrator`, que grava o resultado final no S3.
- OCR, anonimização, agrupamento de documentos ou raciocínio de IA — isso é feito pelo `triaige-srv-mcp-ai`.
- Provisionamento de infraestrutura (rede, filas, banco) — isso é feito pelo `triaige-infra`.
- Outros canais de notificação além de e-mail (SMS, WhatsApp) — não implementados nesta fase, mas suportados pela arquitetura via novos adapters de `NotificationChannelPort`.

## Arquitetura

Arquitetura hexagonal (ports & adapters), validada automaticamente por `HexagonalArchitectureTest` (ArchUnit: `domain`/`application` não podem depender de `jakarta.mail`, `com.mysql`, `software.amazon.awssdk`, `org.springframework` ou `java.sql`):

```text
domain/         modelo + regras puras (Recipient, NotificationMessage, DeliveryResult,
                DeliveryStatus, DeliveryErrorCode, RetryTicket, ResultsReadyEvent) e o
                factory de conteúdo do e-mail (NotificationContentFactory) — zero dependência
                de Spring/SMTP/JDBC/AWS SDK
application/    ports (in/out) e casos de uso (SendCaseNotificationUseCase,
                RetryCaseNotificationUseCase, NotificationSender, DeliveryOutcomeHandler,
                NotificationOutcomeReporter) — classes Java simples, sem anotações Spring
adapter/
  in/sqs/       consumers de Q3 (ResultsReadyConsumer) e da fila de retry (RetryQueueConsumer)
  out/email/    EmailSmtpGmailAdapter (única implementação de NotificationChannelPort) e
                NotificationMetrics
  out/s3/       S3ReportContentAdapter — leitura do relatório completo (bucket curated)
  out/http/     OrchestratorCallbackHttpAdapter — reporta o resultado final ao Orchestrator
  out/persistence/  adapters MySQL via JdbcTemplate (delivery, recipient, audit)
  out/sqs/      publisher da fila de retry/DLQ
config/         wiring Spring: beans de infra + construção manual dos casos de uso
```

### Comunicação com outros serviços

| Serviço / Recurso | Tipo | Finalidade |
|---|---|---|
| `triaige-srv-orchestrator` | HTTP / REST (callback) | Reporta o resultado final da notificação da sessão (`notification-result`) |
| Fila de resultados (Q3) | SQS (consumer) | Recebe o "resultado pronto" publicado pelo Orchestrator |
| Fila de retry + DLQ | SQS (producer/consumer) | Reagendamento de envios falhos e fallback ao esgotar tentativas |
| MySQL | Banco de dados | Persistência de destinatários, entregas e auditoria (schema compartilhado `triaige`) |
| S3 (bucket *curated*) | Storage | Leitura do relatório de triagem completo já anonimizado |
| SMTP (Gmail) | API externa | Envio dos e-mails de notificação |

## Tecnologias utilizadas

- Java 21 + Spring Boot 3.5 (Web, Mail, JDBC, Validation, Actuator)
- `spring-boot-starter-jdbc` com `JdbcTemplate` (sem JPA/Hibernate — upserts idempotentes e leituras simples)
- MySQL (via `mysql-connector-j`)
- AWS SDK v2 — SQS, S3
- `commonmark` + extensão de tabelas GFM (renderização do relatório Markdown como HTML no corpo do e-mail)
- Lombok
- Logback com `logstash-logback-encoder` (logs estruturados em JSON)
- Micrometer + CloudWatch (métricas)
- Docker / Docker Compose
- JUnit 5, ArchUnit, GreenMail (SMTP de teste), Testcontainers (MySQL)

## Estrutura do projeto

```text
src/
├── main/
│   ├── java/br/com/triaige/notification/
│   │   ├── domain/         modelo e regras puras
│   │   ├── application/    ports e casos de uso
│   │   ├── adapter/        in/sqs, out/email, out/s3, out/http, out/persistence, out/sqs
│   │   └── config/         wiring de beans e casos de uso
│   └── resources/
│       └── application.yml
└── test/
    └── ...
```

## Pré-requisitos

- Java 21+
- Maven (ou o wrapper `mvnw`)
- Docker e Docker Compose
- MySQL acessível com o schema `triaige` compartilhado (provisionado via Ansible em `triaige-infra`)
- Credenciais AWS válidas (fila real na AWS — não há LocalStack) e uma conta Gmail com App Password para o SMTP

## Configuração

### Variáveis de ambiente

Copie/ajuste o `.env` na raiz do projeto a partir do `.env.example`:

```env
DB_URL=
DB_USERNAME=
DB_PASSWORD=

SMTP_HOST=
SMTP_PORT=
SMTP_USER=
SMTP_APP_PASSWORD=
NOTIFICATION_FROM_NAME=

DASHBOARD_BASE_URL=

AWS_REGION=
RESULTS_READY_QUEUE_NAME=
NOTIFICATION_RETRY_QUEUE_NAME=
NOTIFICATION_RETRY_DLQ_NAME=
RETRY_CONSUMER_ENABLED=
RESULTS_READY_CONSUMER_ENABLED=

ORCHESTRATOR_BASE_URL=
NOTIFICATION_INTERNAL_TOKEN=

CLOUDWATCH_METRICS_ENABLED=
LOG_LEVEL=
SERVER_PORT=
```

> Nunca versione credenciais, tokens, senhas ou outros secrets reais no repositório.

### Configuração local

`application.yml` reflete o cenário suportado: MySQL real via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` (schema compartilhado com os demais serviços) e filas SQS resolvidas direto na AWS pelo **nome** (`GetQueueUrl`, com cache em memória) — não há LocalStack nem URL fixa em configuração. Rodar localmente exige credenciais AWS válidas no ambiente (variáveis de ambiente, perfil `~/.aws/credentials`, ou IAM role em produção) e as filas já existirem na região configurada.

> O callback ao Orchestrator (`POST .../sessions/{sessionId}/notification-result`) exige `NOTIFICATION_INTERNAL_TOKEN` combinado com o mesmo valor configurado no `.env` do `triaige-srv-orchestrator`. É um reporte best-effort (1 retry com backoff fixo): se as duas tentativas falharem, o serviço apenas loga o erro — o envio do e-mail em si não é afetado.

> As filas de retry/DLQ (`NOTIFICATION_RETRY_QUEUE_NAME`/`NOTIFICATION_RETRY_DLQ_NAME`) precisam existir na AWS antes de habilitar `RETRY_CONSUMER_ENABLED=true`, senão o consumer loga erro a cada poll tentando resolver uma fila inexistente.

## Executando localmente

### Executando com Docker

```bash
docker compose up --build
```

Sobe o próprio serviço (porta `SRV_NOTIFICATION_PORT`, padrão `8083`), usando o MySQL local compartilhado com os demais serviços.

Para encerrar:

```bash
docker compose down
```

### Executando sem Docker

```bash
mvn spring-boot:run
```

Exige o MySQL compartilhado acessível e credenciais AWS reais para o consumidor de fila.

## Testes

```bash
mvn test
```

Cobertura: regra de roteamento e casos de uso com fakes (`SendCaseNotificationUseCaseTest`, `RetryCaseNotificationUseCaseTest`), classificação de erro e renderização HTML do adapter SMTP contra um servidor de teste em memória (`EmailSmtpGmailAdapterTest`, GreenMail), leitura do relatório no S3 (`S3ReportContentAdapterTest`), concorrência do claim atômico com MySQL real via Testcontainers (`MySqlDeliveryAdapterConcurrencyTest`) e a regra arquitetural hexagonal (`HexagonalArchitectureTest`, ArchUnit).

## API

Este serviço não expõe endpoints REST de negócio — é primariamente um worker SQS. O único endpoint HTTP exposto é o de observabilidade:

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/actuator/health` | Health check do processo (não reflete disponibilidade do SMTP — ver Observabilidade) |

## Fluxo principal

```text
Q3: resultado pronto (publicado pelo Orchestrator)
   ↓
Claim atômico da entrega (evita reprocessamento duplicado em redelivery do SQS)
   ↓
Busca do relatório completo no S3 (bucket curated)
   ↓
Resolução dos destinatários cadastrados da sessão
   ↓
Envio de e-mail por destinatário (SMTP Gmail, corpo em HTML)
   ↓
Falha retentável → agenda retry (backoff crescente) → esgotou tentativas → DLQ
   ↓
Callback ao Orchestrator com o resultado final da sessão
```

## Mensageria

| Fila | Tipo | Uso |
|---|---|---|
| `triaige-results-ready` (Q3) | Consumer | Contrato Orchestrator → Notification (mensagem mínima com bucket/chave do relatório) |
| `triaige-notification-retry` (+ DLQ) | Producer/Consumer | Retry de envio com `DelaySeconds` crescente (30s/2min/10min); DLQ ao esgotar `maxReceiveCount` |

## Banco de dados

### Banco utilizado

`MySQL` (schema `triaige`, compartilhado com os demais serviços)

### Principais entidades

- `notification_recipients`: destinatários cadastrados da sessão, com vínculo opcional a `law_firm_contacts`.
- `notification_deliveries`: cada tentativa de envio, com canal, destino, status e claim atômico por sessão/destinatário/canal.
- `audit_events` / `processing_steps`: trilha de auditoria compartilhada com os demais serviços.

## Integrações externas

### SMTP (Gmail)

**Finalidade:** envio dos e-mails de notificação ao escritório.

**Tipo de comunicação:** SMTP com STARTTLS, autenticação por App Password (`spring-boot-starter-mail` / `JavaMailSender`).

### triaige-srv-orchestrator

**Finalidade:** origem do evento de resultado pronto (Q3) e destino do callback com o resultado final da notificação.

**Tipo de comunicação:** SQS (consumer) + HTTP/REST (callback com `X-Internal-Token`).

## Docker

### Build da imagem

```bash
docker build -t triaige-srv-notification .
```

### Executando a imagem

```bash
docker run \
  -p 8083:8083 \
  --env-file .env \
  triaige-srv-notification
```

## Observabilidade

- Logs estruturados em JSON (`logstash-logback-encoder`) — nunca conteúdo do relatório ou dado pessoal.
- Métricas via Micrometer (`NotificationSentCount`, `NotificationFailureCount`, `NotificationLatencyMs`, `SmtpAuthErrorCount`), exportação para CloudWatch desabilitada por padrão (`CLOUDWATCH_METRICS_ENABLED=true` para habilitar; namespace `Triaige/Notification`).
- Health Check: `GET /actuator/health` — reflete apenas a saúde do processo, não a disponibilidade do SMTP (indicador de saúde do `spring-boot-starter-mail` desabilitado via `management.health.mail.enabled=false`), já que uma instabilidade do Gmail já tem tratamento próprio via retry/DLQ e não deve derrubar o health check das instâncias replicadas.

## Segurança

- Callback ao Orchestrator autenticado por token interno (`X-Internal-Token`), chamada servidor-a-servidor não exposta via API Gateway.
- Secrets (credenciais SMTP, credenciais AWS, senha do banco, token interno) configurados via variáveis de ambiente, fora do código.
- Relatório de triagem já chega anonimizado (anonimização feita pelo `triaige-srv-mcp-ai`) antes de compor o corpo do e-mail.

## Repositórios relacionados

Este repositório faz parte do ecossistema **TriAIge**.

| Repositório | Responsabilidade |
|---|---|
| `triaige-front-nextjs` | Site institucional e mockup de dashboard (Next.js / Tailwind) |
| `triaige-srv-orchestrator` | Ingestão de documentos e orquestração do fluxo de triagem |
| `triaige-srv-mcp-ai` | Pipeline de pré-processamento (OCR, anonimização, agrupamento) e raciocínio de IA |
| `triaige-infra` | Provisionamento de infraestrutura AWS (Terraform + Ansible) |

## Projeto acadêmico

Projeto desenvolvido como Trabalho de Conclusão de Curso em:

**Curso:** Sistemas de Informação
**Instituição:** São Paulo Tech School
**Ano:** 2026

### Objetivo

Aplicar IA generativa e engenharia de software para automatizar a triagem inicial de casos jurídicos, reduzindo o tempo de análise manual de documentos por escritórios de advocacia, com anonimização de dados sensíveis antes de qualquer processamento por IA.

## Equipe

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/GabrielNunees063">
        <img src="https://avatars.githubusercontent.com/u/125298578?v=4" width="100px;"><br>
        <sub><b>Gabriel Nunes</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/Bielzinschiavo">
        <img src="https://avatars.githubusercontent.com/u/125298078?v=4" width="100px;"><br>
        <sub><b>Gabriel Schiavo</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/gyuliapiqueira">
        <img src="https://avatars.githubusercontent.com/u/125298346?v=4" width="100px;"><br>
        <sub><b>Gyulia Piqueira</b></sub>
      </a>
    </td>
  </tr>

  <tr>
    <td align="center" colspan="3">
      <table>
        <tr>
          <td align="center">
            <a href="https://github.com/Kaori2">
              <img src="https://avatars.githubusercontent.com/u/125297000?v=4" width="100px;"><br>
              <sub><b>Kaori Katayama</b></sub>
            </a>
          </td>
          <td align="center">
            <a href="https://github.com/Miguel-Araujo325">
              <img src="https://avatars.githubusercontent.com/u/125296970?v=4" width="100px;"><br>
              <sub><b>Miguel Araujo</b></sub>
            </a>
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>

## Licença

> Este projeto foi desenvolvido para fins acadêmicos.
