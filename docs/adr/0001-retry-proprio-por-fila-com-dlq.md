# 0001 — Retry próprio por fila com DLQ

- Status: aceita
- Data: 2026-09

## Contexto
O envio usa o SMTP do Gmail, que pode recusar mensagens por limite de taxa, falha de autenticação ou instabilidade. Uma sessão tem vários destinatários, e cada um pode falhar de forma diferente. A fila de entrada (`triaige-results-ready`) traz uma sessão por mensagem: refazer a mensagem inteira reenviaria o e-mail para quem já recebeu.

## Decisão
Cada envio por destinatário é registrado em `notification_deliveries`. Falhas retentáveis são reagendadas na fila própria `triaige-notification-retry`, com atraso crescente (30 segundos, 2 minutos e 10 minutos), uma mensagem por destinatário. Esgotadas as tentativas, a mensagem vai para `triaige-notification-retry-dlq` e o resultado final é informado ao orchestrator.

## Alternativas consideradas
- Retry síncrono no envio: simples de implementar, mas segura a thread do consumidor durante as esperas, estoura o visibility timeout da mensagem da `triaige-results-ready` e, numa nova entrega, reenviaria para todos os destinatários.

## Consequências
- Só quem falhou recebe nova tentativa, e o claim atômico em `notification_deliveries` impede envio duplicado quando o SQS entrega a mesma mensagem duas vezes.
- O consumidor da fila principal nunca fica parado esperando.
- Em troca, há uma fila e uma DLQ a mais para provisionar, e o resultado final da sessão só é conhecido depois da última tentativa.
- Um Gmail fora do ar não derruba o health check do serviço (`management.health.mail.enabled=false`).
