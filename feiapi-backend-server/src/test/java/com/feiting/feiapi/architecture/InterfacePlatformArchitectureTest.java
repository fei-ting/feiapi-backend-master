package com.feiting.feiapi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 接口平台能力域架构约束测试。
 *
 * <p>当前仅约束新建的接口平台能力域包，避免历史全局包在阶段 1 被迫一次性迁移。</p>
 */
@AnalyzeClasses(packages = "com.feiting.feiapi.interfaceplatform", importOptions = ImportOption.DoNotIncludeTests.class)
class InterfacePlatformArchitectureTest {

    /**
     * Controller 层不得直接依赖 Mapper 层。
     */
    @ArchTest
    static final ArchRule controllers_should_not_depend_on_mappers =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .allowEmptyShould(true);

    /**
     * 文档域不得依赖发布域。
     */
    @ArchTest
    static final ArchRule documentation_should_not_depend_on_publishing =
            noClasses()
                    .that().resideInAPackage("..interfaceplatform.documentation..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaceplatform.publishing..");

    /**
     * 定义域不得依赖文档域、发布域或生命周期域。
     */
    @ArchTest
    static final ArchRule definition_should_not_depend_on_other_domains =
            noClasses()
                    .that().resideInAPackage("..interfaceplatform.definition..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaceplatform.documentation..",
                            "..interfaceplatform.publishing..",
                            "..interfaceplatform.lifecycle..");

    /**
     * 生命周期域不得依赖文档域或发布域。
     */
    @ArchTest
    static final ArchRule lifecycle_should_not_depend_on_documentation_or_publishing =
            noClasses()
                    .that().resideInAPackage("..interfaceplatform.lifecycle..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaceplatform.documentation..",
                            "..interfaceplatform.publishing..");

    /**
     * 发布域不得依赖其他能力域的 Mapper、实体或内部实现。
     */
    @ArchTest
    static final ArchRule publishing_should_not_depend_on_other_domain_internals =
            noClasses()
                    .that().resideInAPackage("..interfaceplatform.publishing..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaceplatform.definition..mapper..",
                            "..interfaceplatform.definition..model.entity..",
                            "..interfaceplatform.definition..service.impl..",
                            "..interfaceplatform.documentation..mapper..",
                            "..interfaceplatform.documentation..model.entity..",
                            "..interfaceplatform.documentation..service.impl..",
                            "..interfaceplatform.lifecycle..mapper..",
                            "..interfaceplatform.lifecycle..model.entity..",
                            "..interfaceplatform.lifecycle..service.impl..")
                    .allowEmptyShould(true);

    /**
     * 协调层不得依赖任何 Mapper。
     */
    @ArchTest
    static final ArchRule facade_should_not_depend_on_mappers =
            noClasses()
                    .that().resideInAPackage("..interfaceplatform.facade..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..")
                    .allowEmptyShould(true);

    /**
     * 其他能力域不得反向依赖协调层。
     */
    @ArchTest
    static final ArchRule domains_should_not_depend_on_facade =
            noClasses()
                    .that().resideInAnyPackage(
                            "..interfaceplatform.definition..",
                            "..interfaceplatform.documentation..",
                            "..interfaceplatform.publishing..",
                            "..interfaceplatform.lifecycle..")
                    .should().dependOnClassesThat().resideInAPackage("..interfaceplatform.facade..");
}
