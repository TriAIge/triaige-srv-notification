package br.com.triaige.notification.adapter.out.s3;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sem LocalStack: {@link S3Client} mockado (Mockito) - suficiente para validar a montagem da
 * requisicao e a decodificacao da resposta, sem depender de infraestrutura AWS real em teste.
 */
class S3ReportContentAdapterTest {

    @Test
    void fetchMarkdown_devolveConteudoDecodificado() {
        S3Client s3Client = mock(S3Client.class);
        String markdown = "# Relatorio\n\nConteudo em UTF-8: ção, ã, é.";
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(response);

        S3ReportContentAdapter adapter = new S3ReportContentAdapter(s3Client);

        Optional<String> result = adapter.fetchMarkdown("meu-bucket", "lawfirm/session/relatorio.md");

        assertThat(result).contains(markdown);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        org.mockito.Mockito.verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("meu-bucket");
        assertThat(captor.getValue().key()).isEqualTo("lawfirm/session/relatorio.md");
    }

    @Test
    void fetchMarkdown_objetoInexistente_devolveEmptySemLancarExcecao() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        S3ReportContentAdapter adapter = new S3ReportContentAdapter(s3Client);

        Optional<String> result = adapter.fetchMarkdown("meu-bucket", "chave-inexistente.md");

        assertThat(result).isEmpty();
    }
}
