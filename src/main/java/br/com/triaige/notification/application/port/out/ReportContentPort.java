package br.com.triaige.notification.application.port.out;

import java.util.Optional;

/** Busca o relatório em Markdown a partir de {@code resultBucket} e {@code summaryObjectKey} da mensagem. */
public interface ReportContentPort {

    /**
     * @return o Markdown do relatório, ou vazio se não foi possível ler; tratado como falha retentável
     *         ({@link br.com.triaige.notification.domain.model.DeliveryErrorCode#REPORT_FETCH_ERROR}).
     */
    Optional<String> fetchMarkdown(String bucket, String objectKey);
}
