package br.com.triaige.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolve a URL real de uma fila SQS diretamente na AWS a partir do nome ({@code GetQueueUrl}),
 * em vez de depender de uma URL fixa em configuracao/endpoint override (esse era o unico motivo
 * para existir um "endpoint customizado" estilo LocalStack nos outros servicos — aqui nao existe).
 * Resultado cacheado em memoria: o nome->URL de uma fila SQS nao muda em runtime.
 */
@Component
public class SqsQueueUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(SqsQueueUrlResolver.class);

    private final SqsClient sqsClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SqsQueueUrlResolver(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    public String resolve(String queueName) {
        return cache.computeIfAbsent(queueName, this::fetchFromAws);
    }

    private String fetchFromAws(String queueName) {
        try {
            String url = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
            log.info("Fila '{}' resolvida na AWS: {}", queueName, url);
            return url;
        } catch (QueueDoesNotExistException ex) {
            throw new IllegalStateException("Fila SQS '" + queueName + "' nao existe na AWS/regiao configurada "
                    + "(ver secao 11.2 da spec - provisionamento em triaige-infra)", ex);
        }
    }

    /** Usado apos falha de envio para invalidar um cache potencialmente obsoleto (ex.: fila recriada). */
    public void invalidate(String queueName) {
        cache.remove(queueName);
    }
}
