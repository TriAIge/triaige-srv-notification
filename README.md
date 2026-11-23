# TriAIge / triaige-srv-notification

> Serviço de notificação por e-mail: envia o relatório final da triagem aos destinatários do escritório.

![Java](https://img.shields.io/badge/Java_21-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-4479A1?style=flat-square&logo=mysql&logoColor=white)
![AWS](https://img.shields.io/badge/AWS_S3_%7C_SQS-232F3E?style=flat-square&logo=amazonwebservices&logoColor=white)
![Licença](https://img.shields.io/badge/licen%C3%A7a-MIT-blue?style=flat-square)

Parte da plataforma [TriAIge](https://github.com/TriAIge) — triagem jurídica assistida por IA.

## Sobre o projeto

Este serviço é um worker SQS que fecha o fluxo de triagem do lado do escritório. Consome o "resultado pronto" publicado pelo `triaige-srv-orchestrator`, busca o relatório no S3 *curated* e envia um e-mail por destinatário cadastrado, com retry e DLQ em caso de falha, reportando o resultado final ao orchestrator.

## Papel na arquitetura

```text
triaige-srv-orchestrator
   ↓ 1. Q3 triaige-results-ready (resultado pronto)
triaige-srv-notification
   ├── 2. claim atômico da entrega (evita envio duplicado em redelivery do SQS)
   ├── 3. busca o relatório (Markdown) no S3 curated e renderiza como HTML
   ├── 4. resolve os destinatários da sessão
   ├── 5. envia um e-mail por destinatário (SMTP Gmail)
   ├── 6. falha retentável → triaige-notification-retry (30s, 2min, 10min) → DLQ
   └── 7. callback POST /api/orchestrator/v1/sessions/{id}/notification-result ──→ triaige-srv-orchestrator
```

## Responsabilidades

- Consumir a fila `triaige-results-ready` (Q3).
- Buscar o relatório já anonimizado no S3 *curated* e renderizá-lo como HTML no corpo do e-mail.
- Resolver os destinatários da sessão e enviar um e-mail por destinatário via SMTP.
- Registrar cada tentativa em `notification_deliveries`, com claim atômico para nunca duplicar um envio em andamento.
- Reagendar falhas retentáveis na fila de retry e mover para a DLQ ao esgotar as tentativas.
- Reportar ao orchestrator o resultado final da sessão (sucesso, falha parcial ou falha total).
- Auditoria e métricas do fluxo.

### Fora do escopo

- Gerar ou renderizar o relatório — `triaige-srv-orchestrator`.
- OCR, anonimização, agrupamento e raciocínio de IA — `triaige-srv-mcp-ai`.
- Cadastro dos contatos do escritório — `triaige-front-nextjs`.
- Provisionamento de rede, filas e banco — `triaige-infra`.
- Canais além de e-mail (SMS, WhatsApp): não implementados, mas a arquitetura os suporta por novos adapters de `NotificationChannelPort`.

## Arquitetura interna

Decisões de arquitetura registradas em [`docs/adr/`](docs/adr/):

- [0001 — Retry próprio por fila com DLQ](docs/adr/0001-retry-proprio-por-fila-com-dlq.md)

Arquitetura hexagonal (ports & adapters), validada por `HexagonalArchitectureTest` (ArchUnit: `domain` e `application` não podem depender de `jakarta.mail`, `com.mysql`, `software.amazon.awssdk`, `org.springframework` nem `java.sql`).

```text
src/main/java/br/com/triaige/notification/
├── domain/             modelo e regras puras (Recipient, NotificationMessage, DeliveryResult,
│                       DeliveryStatus, DeliveryErrorCode, RetryTicket, ResultsReadyEvent) e
│                       NotificationContentFactory, sem dependência de Spring, SMTP, JDBC ou AWS
├── application/        ports (in/out) e casos de uso (SendCaseNotificationUseCase,
│                       RetryCaseNotificationUseCase, NotificationSender, DeliveryOutcomeHandler,
│                       NotificationOutcomeReporter), sem anotações Spring
├── adapter/
│   ├── in/sqs/         ResultsReadyConsumer (Q3) e RetryQueueConsumer
│   ├── out/email/      EmailSmtpGmailAdapter (única NotificationChannelPort) e NotificationMetrics
│   ├── out/s3/         S3ReportContentAdapter (leitura do relatório no curated)
│   ├── out/http/       OrchestratorCallbackHttpAdapter
│   ├── out/persistence/ adapters MySQL via JdbcTemplate (entregas, destinatários, auditoria)
│   └── out/sqs/        publisher da fila de retry
└── config/             wiring Spring e construção manual dos casos de uso
```

### Comunicação com outros serviços

| Serviço / Recurso | Tipo | Finalidade |
|---|---|---|
| `triaige-srv-orchestrator` | HTTP (callback) | `POST /api/orchestrator/v1/sessions/{id}/notification-result` |
| `triaige-results-ready` (Q3) | SQS (consumer) | "Resultado pronto" publicado pelo orchestrator |
| `triaige-notification-retry` | SQS (producer/consumer) | Reagendamento de envios falhos |
| S3 *curated* | Storage | Leitura do relatório final |
| SMTP Gmail | Serviço externo | Envio dos e-mails |
| MySQL (schema `triaige`) | Banco de dados | Compartilhado com os demais serviços |

## Tecnologias

- Java 21, Spring Boot 3.5.0 (Web, Mail, JDBC, Validation, Actuator)
- `JdbcTemplate`, sem JPA/Hibernate
- MySQL 8 (`mysql-connector-j`)
- AWS SDK v2 (SQS, S3)
- `commonmark` com extensão de tabelas GFM (Markdown → HTML)
- Lombok
- Logback com `logstash-logback-encoder` (logs JSON)
- Micrometer com exportação para CloudWatch
- JUnit 5, ArchUnit, GreenMail, Testcontainers (MySQL)
- Docker / Docker Compose

## Pré-requisitos

- Java 21 e Maven
- Docker e Docker Compose
- MySQL acessível com o schema `triaige` (o seed está em [`triaige-infra`](https://github.com/TriAIge/triaige-infra), `ansible/files/triaige-schema-seed.sql`)
- Credenciais AWS válidas e as filas já existentes na região (não há LocalStack)
- Uma conta Gmail com App Password para o SMTP

## Configuração

Copie o `.env.example` para `.env` e preencha os valores. As filas são resolvidas na AWS pelo **nome** (`GetQueueUrl`, com cache em memória), sem URL fixa.

| Variável | Obrigatória | Padrão | Descrição |
|---|---|---|---|
| `DB_URL` | Sim | `jdbc:mysql://localhost:3306/triaige?...` | URL JDBC do schema compartilhado |
| `DB_USERNAME` / `DB_PASSWORD` | Sim | `triaige` / `triaige` | Credenciais do MySQL |
| `SMTP_HOST` / `SMTP_PORT` | Não | `smtp.gmail.com` / `587` | Servidor SMTP (STARTTLS) |
| `SMTP_USER` | Sim | vazio | Conta Gmail remetente |
| `SMTP_APP_PASSWORD` | Sim | vazio | App Password de 16 dígitos (não a senha da conta) |
| `NOTIFICATION_FROM_NAME` | Não | `Triaige` | Nome exibido como remetente |
| `DASHBOARD_BASE_URL` | Não | `http://localhost:3000` | URL do portal usada no link do e-mail |
| `AWS_REGION` | Não | `us-east-1` | Região AWS |
| `AWS_PROFILE` / `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN` | Não | — | Credenciais AWS; no Compose, `~/.aws` é montado no container |
| `RESULTS_READY_QUEUE_NAME` | Não | `triaige-results-ready` | Fila Q3 |
| `NOTIFICATION_RETRY_QUEUE_NAME` | Não | `triaige-notification-retry` | Fila de retry |
| `NOTIFICATION_RETRY_DLQ_NAME` | Não | `triaige-notification-retry-dlq` | DLQ do retry |
| `RESULTS_READY_CONSUMER_ENABLED` / `RETRY_CONSUMER_ENABLED` | Não | `true` / `true` | Liga cada consumidor |
| `ORCHESTRATOR_BASE_URL` | Sim | `http://localhost:8080` | URL base do orchestrator |
| `NOTIFICATION_INTERNAL_TOKEN` | Sim | `dev-local-internal-token` | Token enviado no callback; igual ao do orchestrator |
| `SERVER_PORT` | Não | `8083` | Porta HTTP |
| `SRV_NOTIFICATION_PORT` | Não | `8083` | Porta publicada no host pelo Compose |
| `CLOUDWATCH_METRICS_ENABLED` | Não | `false` | Exporta métricas para o CloudWatch |
| `LOG_LEVEL` | Não | `INFO` | Nível de log de `br.com.triaige` |

Os padrões existem só para desenvolvimento. Nunca versione credenciais, tokens ou senhas reais.

O callback ao orchestrator é best-effort (1 retry com backoff fixo): se as duas tentativas falharem, o serviço só registra o erro e o envio do e-mail não é afetado.

## Executando localmente

### Docker Compose

```bash
docker compose up --build
```

Sobe só o serviço, na porta `SRV_NOTIFICATION_PORT` (padrão `8083`). O MySQL não faz parte do Compose: `DB_URL` precisa apontar para um banco alcançável de dentro do container (por exemplo, `host.docker.internal:3306`). Para encerrar, `docker compose down`.

### Sem Docker

```bash
mvn spring-boot:run
```

Exige o MySQL acessível em `DB_URL` e credenciais AWS no ambiente.

### Build e run da imagem

```bash
docker build -t triaige-srv-notification .
docker run -p 8083:8083 --env-file .env triaige-srv-notification
```

## Testes

```bash
mvn test
```

Cobrem os casos de uso com fakes (`SendCaseNotificationUseCaseTest`, `RetryCaseNotificationUseCaseTest`), a classificação de erro e a renderização HTML contra um SMTP em memória (`EmailSmtpGmailAdapterTest`, GreenMail), a leitura do relatório (`S3ReportContentAdapterTest`), a concorrência do claim atômico com MySQL real via Testcontainers (`MySqlDeliveryAdapterConcurrencyTest`), a configuração de métricas (`CloudWatchMetricsConfigTest`) e a regra hexagonal (`HexagonalArchitectureTest`).

## API

O serviço não expõe endpoints de negócio. O único endpoint HTTP é o de saúde:

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/actuator/health` | Saúde do processo (não reflete a disponibilidade do SMTP) |

O serviço **chama** `POST /api/orchestrator/v1/sessions/{id}/notification-result`, uma chamada servidor-a-servidor autenticada por `X-Internal-Token`. A rota fica atrás do ALB (`/api/orchestrator/*`), portanto é alcançável pela internet; o token é a única proteção e precisa ser forte e mantido em segredo.

## Mensageria

| Fila | Papel | Uso |
|---|---|---|
| `triaige-results-ready` (Q3) | Consumer | Contrato orchestrator → notification (mensagem mínima com bucket e chave do relatório) |
| `triaige-notification-retry` | Producer e consumer | Retry com `DelaySeconds` crescente (30s, 2min, 10min) |
| `triaige-notification-retry-dlq` | DLQ | Recebe a mensagem ao esgotar o `maxReceiveCount` (3) |

## Banco de dados

MySQL, schema `triaige`, compartilhado com os demais serviços. O schema é aplicado pelo `triaige-infra`.

| Tabela | Acesso | Uso |
|---|---|---|
| `notification_recipients` | Leitura | Destinatários da sessão (nome, e-mail, canal), gravados pelo orchestrator ao criar a sessão |
| `law_firm_contacts` | Leitura | Exclui destinatários cujo contato foi inativado depois da criação da sessão |
| `notification_deliveries` | Leitura e escrita | Cada tentativa de envio, com status e claim atômico por sessão, destinatário e canal |
| `audit_events`, `processing_steps` | Escrita | Auditoria e etapa de notificação |

Os destinatários vêm de `law_firm_contacts`, cadastrados pelo escritório no portal do [`triaige-front-nextjs`](https://github.com/TriAIge/triaige-front-nextjs) (`/portal/config`): o orchestrator copia os contatos ativos para `notification_recipients` quando a sessão é criada.

## Integrações externas

- **SMTP Gmail:** envio dos e-mails com STARTTLS, autenticado por App Password (`spring-boot-starter-mail` / `JavaMailSender`).
- **AWS S3:** leitura do relatório no bucket *curated*.
- **AWS SQS:** filas Q3 e de retry.

## Observabilidade

- Logs JSON (`logstash-logback-encoder`), sem conteúdo do relatório nem dado pessoal. Em produção vão para o log group `/triaige/triaige-srv-notification`.
- Métricas Micrometer no namespace `Triaige/Notification` (`NotificationSentCount`, `NotificationFailureCount`, `NotificationLatencyMs`, `SmtpAuthErrorCount`), exportadas com `CLOUDWATCH_METRICS_ENABLED=true`.
- Health check: `GET /actuator/health`. O indicador de saúde do SMTP está desligado (`management.health.mail.enabled=false`), porque uma instabilidade do Gmail já é tratada por retry e DLQ e não deve derrubar as instâncias no ALB.

## Segurança

- Callback ao orchestrator autenticado por `X-Internal-Token` (ver [API](#api) sobre a exposição pelo ALB).
- Secrets (SMTP, AWS, banco, token interno) via variáveis de ambiente, fora do código.
- O relatório chega já anonimizado pelo `triaige-srv-mcp-ai`.

## Repositórios relacionados

| Repositório | Responsabilidade |
|---|---|
| [`triaige-front-nextjs`](https://github.com/TriAIge/triaige-front-nextjs) | Portal dos escritórios e painel da equipe TriAIge (Next.js), com API própria sobre o banco compartilhado |
| [`triaige-srv-orchestrator`](https://github.com/TriAIge/triaige-srv-orchestrator) | Ingestão de documentos, orquestração do fluxo e geração do relatório final |
| [`triaige-srv-mcp-ai`](https://github.com/TriAIge/triaige-srv-mcp-ai) | Pré-processamento (OCR, anonimização, agrupamento, resumo) e análise com IA |
| [`triaige-infra`](https://github.com/TriAIge/triaige-infra) | Infraestrutura AWS (Terraform, Ansible e GitHub Actions) |

## Projeto acadêmico

Trabalho de Conclusão de Curso de Sistemas de Informação na São Paulo Tech School (SPTech), 2026. Equipe e visão geral no [perfil da organização](https://github.com/TriAIge).

## Licença

Distribuído sob a licença MIT — ver [LICENSE](LICENSE).
