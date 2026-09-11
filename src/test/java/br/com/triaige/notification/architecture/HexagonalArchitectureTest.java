package br.com.triaige.notification.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Critério de aceite principal: domain/application nao podem importar
 * nenhuma biblioteca de infraestrutura (SMTP, driver MySQL, SDK AWS, cliente SQS) — nem, por
 * escolha deste servico, o proprio framework Spring, para que os casos de uso sejam construidos
 * manualmente em config/ com valores/ports simples (ver UseCaseConfig).
 */
class HexagonalArchitectureTest {

    private static final com.tngtech.archunit.core.domain.JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.triaige.notification");

    @Test
    void domainAndApplicationMustNotDependOnMailLibraries() {
        rule("jakarta.mail..", "com.sun.mail..").check(CLASSES);
    }

    @Test
    void domainAndApplicationMustNotDependOnMySqlDriver() {
        rule("com.mysql..").check(CLASSES);
    }

    @Test
    void domainAndApplicationMustNotDependOnAwsSdk() {
        rule("software.amazon.awssdk..").check(CLASSES);
    }

    @Test
    void domainAndApplicationMustNotDependOnSpringFramework() {
        rule("org.springframework..").check(CLASSES);
    }

    @Test
    void domainAndApplicationMustNotDependOnJdbc() {
        rule("java.sql..", "javax.sql..").check(CLASSES);
    }

    private ArchRule rule(String... forbiddenPackages) {
        return noClasses()
                .that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenPackages);
    }
}
