package br.com.triaige.notification.domain.model;

import java.util.UUID;

/**
 * Representacao de dominio do evento Q3 ({@code triaige-results-ready}), ja validado
 * (schemaVersion suportada) pelo adapter de entrada.
 *
 * <p><b>Decisao de produto:</b> o corpo do e-mail passou
 * a incluir o relatorio de triagem completo (buscado do S3 via {@code resultBucket}/
 * {@code summaryObjectKey}), nao mais apenas protocolo+link. Decisao explicita do responsavel pelo
 * produto, ciente do trade-off de expor o relatorio (ja anonimizado) em um canal sem MFA/controle
 * de acesso — o risco de uma caixa de e-mail comprometida do lado do escritorio cliente foi aceito
 * conscientemente como responsabilidade do escritorio, nao do TriAIge.</p>
 */
public record ResultsReadyEvent(
        UUID sessionId,
        UUID correlationId,
        String protocolo,
        UUID lawFirmId,
        String resultBucket,
        String summaryObjectKey
) {
}
