package br.com.triaige.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Cliente SQS conectando direto na AWS real — sem endpoint override, sem LocalStack. Credenciais
 * vem da cadeia padrao do SDK v2 ({@link DefaultCredentialsProvider}: variaveis de ambiente,
 * perfil, ou IAM role da instancia EC2 em producao).
 */
@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class AwsSqsClientConfig {

    @Bean
    public SqsClient sqsClient(AwsProperties awsProperties) {
        return SqsClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
