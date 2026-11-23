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
 * Resolve a URL da fila pelo nome ({@code GetQueueUrl}) e guarda em memória, já que a URL de uma
 * fila não muda enquanto ela existir.
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
            throw new IllegalStateException("Fila SQS '" + queueName + "' não existe na região configurada "
                    + "(as filas são criadas pelo triaige-infra)", ex);
        }
    }

    /** Descarta a URL em cache depois de uma falha de envio (por exemplo, fila recriada). */
    public void invalidate(String queueName) {
        cache.remove(queueName);
    }
}
