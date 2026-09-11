package br.com.triaige.notification.adapter.out.s3;

import br.com.triaige.notification.application.port.out.ReportContentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Busca o relatorio de triagem (markdown) direto na AWS real, sem LocalStack/endpoint override —
 * mesma decisao ja adotada para SQS neste servico.
 */
@Component
public class S3ReportContentAdapter implements ReportContentPort {

    private static final Logger log = LoggerFactory.getLogger(S3ReportContentAdapter.class);

    private final S3Client s3Client;

    public S3ReportContentAdapter(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public Optional<String> fetchMarkdown(String bucket, String objectKey) {
        try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build())) {
            byte[] bytes = object.readAllBytes();
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            log.warn("Falha ao buscar relatorio no S3 bucket={} key={}: {}", bucket, objectKey, e.getMessage());
            return Optional.empty();
        }
    }
}
