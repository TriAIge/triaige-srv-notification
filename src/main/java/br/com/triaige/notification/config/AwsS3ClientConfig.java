package br.com.triaige.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Cliente S3 conectando direto na AWS real — sem endpoint override, sem LocalStack, mesmo padrao
 * do {@link AwsSqsClientConfig}. Usado apenas para buscar o relatorio de triagem (markdown) que
 * compoe o corpo do e-mail (ver {@code S3ReportContentAdapter}).
 */
@Configuration
public class AwsS3ClientConfig {

    @Bean
    public S3Client s3Client(AwsProperties awsProperties) {
        return S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
