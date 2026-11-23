package br.com.triaige.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Cliente SQS direto na AWS, sem endpoint alternativo. Credenciais pela cadeia padrão do SDK
 * (variáveis de ambiente, perfil ou role da EC2).
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
