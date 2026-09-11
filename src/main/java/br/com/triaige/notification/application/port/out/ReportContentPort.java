package br.com.triaige.notification.application.port.out;

import java.util.Optional;

/**
 * Port de saida — busca o conteudo (markdown) do relatorio de triagem no armazenamento de objetos
 * (S3 na implementacao atual), a partir dos ponteiros {@code resultBucket}/{@code summaryObjectKey}
 * que vem no evento Q3. Isola a camada application do SDK de storage usado.
 */
public interface ReportContentPort {

    /**
     * @return o conteudo markdown do relatorio, ou {@link Optional#empty()} se nao foi possivel
     *         buscar (objeto inexistente, erro de rede/permissao) — tratado como falha retentavel
     *         pela camada application ({@link br.com.triaige.notification.domain.model.DeliveryErrorCode#REPORT_FETCH_ERROR}).
     */
    Optional<String> fetchMarkdown(String bucket, String objectKey);
}
