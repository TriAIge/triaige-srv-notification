package br.com.triaige.notification.application;

import br.com.triaige.notification.application.port.out.ReportContentPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StubReportContentPort implements ReportContentPort {

    public record Call(String bucket, String objectKey) {
    }

    public final List<Call> calls = new ArrayList<>();
    private Optional<String> nextResult = Optional.of("# Relatorio de teste\n\nConteudo.");

    public static StubReportContentPort returning(String markdown) {
        StubReportContentPort stub = new StubReportContentPort();
        stub.nextResult = Optional.ofNullable(markdown);
        return stub;
    }

    public static StubReportContentPort failing() {
        StubReportContentPort stub = new StubReportContentPort();
        stub.nextResult = Optional.empty();
        return stub;
    }

    @Override
    public Optional<String> fetchMarkdown(String bucket, String objectKey) {
        calls.add(new Call(bucket, objectKey));
        return nextResult;
    }
}
