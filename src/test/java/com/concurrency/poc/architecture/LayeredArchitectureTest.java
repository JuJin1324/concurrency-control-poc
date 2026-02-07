package com.concurrency.poc.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.concurrency.poc", importOptions = ImportOption.DoNotIncludeTests.class)
public class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule layered_architecture_rule = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("com.concurrency.poc..")
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .layer("Domain").definedBy("..domain..")
            .layer("Dto").definedBy("..dto..")

            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
            // Domain은 Service, Repository, Controller에서 접근 가능
            // Controller는 예외 클래스만 접근 가능 (Entity 직접 사용 금지)
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Repository", "Service", "Controller")
            // DTO는 Controller와 Service에서 접근 가능
            .whereLayer("Dto").mayOnlyBeAccessedByLayers("Controller", "Service")

            // Controller에서 Domain Entity 직접 사용 금지 (예외 클래스만 허용)
            .ignoreDependency(
                    describe("controller classes", (JavaClass clazz) ->
                            clazz.getPackageName().contains("controller")),
                    describe("domain entities (not exceptions)", (JavaClass clazz) ->
                            clazz.getPackageName().contains("domain")
                                    && !clazz.getSimpleName().endsWith("Exception"))
            )
            // GlobalExceptionHandler에서 Domain 예외 클래스 접근 허용
            .ignoreDependency(
                    describe("exception handler classes", (JavaClass clazz) ->
                            clazz.getPackageName().contains("exception")),
                    describe("domain exception classes", (JavaClass clazz) ->
                            clazz.getPackageName().contains("domain")
                                    && clazz.getSimpleName().endsWith("Exception"))
            );
}
