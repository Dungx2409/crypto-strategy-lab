package com.cryptolab.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.cryptolab.strategy.domain.Strategy;
import com.cryptolab.experiment.port.StrategyGenerator;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@AnalyzeClasses(packages = "com.cryptolab", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule TRANSACTIONAL_CLASSES_MUST_BE_PROXYABLE = classes()
            .that()
            .containAnyMethodsThat(DescribedPredicate.describe(
                    "are annotated with @Transactional",
                    (JavaMethod method) -> method.isAnnotatedWith(Transactional.class)))
            .should()
            .notHaveModifier(JavaModifier.FINAL);

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_WEB_FRAMEWORKS = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.web.socket..",
                    "org.springframework.messaging.simp..");

    @ArchTest
    static final ArchRule STRATEGIES_DO_NOT_DEPEND_ON_BACKTEST_ADAPTERS = noClasses()
            .that()
            .implement(Strategy.class)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..experiment.adapter.backtest..", "..backtester..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule EXPERIMENT_AND_API_DO_NOT_DEPEND_ON_BASELINE_STRATEGIES = noClasses()
            .that()
            .resideInAnyPackage("..experiment..", "..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..strategy.domain.baseline..");

    @ArchTest
    static final ArchRule BASELINE_STRATEGIES_DO_NOT_DEPEND_ON_EXPERIMENT_OR_PRESENTATION = noClasses()
            .that()
            .resideInAPackage("..strategy.domain.baseline..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..experiment..", "..api..", "..worker..");

    @ArchTest
    static final ArchRule PIPELINE_PRESENTATION_AND_WORKERS_DO_NOT_DEPEND_ON_MACD_EXTENSION = noClasses()
            .that()
            .resideInAnyPackage("..experiment..", "..api..", "..worker..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameContaining("Macd");

    @ArchTest
    static final ArchRule EXPERIMENT_DOMAIN_DOES_NOT_DEPEND_ON_BINANCE = noClasses()
            .that()
            .resideInAPackage("..experiment.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..binance..", "..marketdata.adapter..");

    @ArchTest
    static final ArchRule NEWS_DOMAIN_DOES_NOT_DEPEND_ON_STRATEGY_INFRASTRUCTURE = noClasses()
            .that()
            .resideInAPackage("..news.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..strategy.adapter..", "com.cryptolab.infrastructure.strategy..");

    @ArchTest
    static final ArchRule MARKET_SEARCH_BACKTEST_AND_LEADERBOARD_DO_NOT_DEPEND_ON_NEWS = noClasses()
            .that()
            .resideInAnyPackage(
                    "..marketdata..",
                    "..experiment..",
                    "..api.search..",
                    "..api.experiment..",
                    "..worker..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.cryptolab.news..", "com.cryptolab.infrastructure.news..");

    @ArchTest
    static final ArchRule NEWS_PROVIDER_ADAPTER_DOES_NOT_DEPEND_ON_SENTIMENT_ADAPTER = noClasses()
            .that()
            .resideInAPackage("..news.adapter.cryptocompare..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("DeterministicKeywordSentimentAnalyzer");

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_ACCESS_REPOSITORIES = noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..repository..", "..persistence..").allowEmptyShould(true);

    @ArchTest
    static final ArchRule CORE_DOES_NOT_DEPEND_ON_OUTER_MODULE_PACKAGES = noClasses()
            .that()
            .resideInAnyPackage(
                    "com.cryptolab.marketdata..",
                    "com.cryptolab.strategy..",
                    "com.cryptolab.experiment..",
                    "com.cryptolab.news..",
                    "com.cryptolab.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.cryptolab.infrastructure..",
                    "com.cryptolab.api..",
                    "com.cryptolab.worker..");

    @ArchTest
    static final ArchRule CORE_DOES_NOT_DEPEND_ON_RABBITMQ = noClasses()
            .that()
            .resideInAnyPackage(
                    "com.cryptolab.marketdata..",
                    "com.cryptolab.strategy..",
                    "com.cryptolab.experiment..",
                    "com.cryptolab.news..",
                    "com.cryptolab.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.amqp..", "com.rabbitmq.client..");

    @ArchTest
    static final ArchRule API_DOES_NOT_DEPEND_ON_BACKTEST_JOB_CONSUMER_CONTRACTS = noClasses()
            .that()
            .resideInAPackage("com.cryptolab.api..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("BacktestJobProcessor")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleName("BacktestJobTopology")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleName("BacktestJobClaim")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleName("BacktestJobClaimDecision");

    @ArchTest
    static final ArchRule BINANCE_DTOS_DO_NOT_LEAK_OUTSIDE_THE_ADAPTER = noClasses()
            .that()
            .resideOutsideOfPackage("..marketdata.adapter.binance..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Dto");

    @ArchTest
    static final ArchRule MARKET_CONTROLLERS_DO_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that()
            .resideInAPackage("..api.marketdata..")
            .and()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.cryptolab.infrastructure..");

    @ArchTest
    static final ArchRule EXPERIMENT_CONTROLLERS_DO_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that()
            .resideInAPackage("..api.experiment..")
            .and()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.cryptolab.infrastructure..");

    @ArchTest
    static final ArchRule EVALUATOR_DOES_NOT_DEPEND_ON_BACKTEST_ENGINE = noClasses()
            .that()
            .haveSimpleName("DefaultExperimentEvaluator")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("DeterministicBacktestEngine");

    @ArchTest
    static final ArchRule RANKING_DOES_NOT_DEPEND_ON_BACKTEST_ENGINE = noClasses()
            .that()
            .haveSimpleName("DefaultRankingService")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("DeterministicBacktestEngine");

    @ArchTest
    static final ArchRule SEARCH_CONTROLLERS_DO_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that()
            .resideInAPackage("..api.search..")
            .and()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.cryptolab.infrastructure..");

    @ArchTest
    static final ArchRule GENERATORS_DO_NOT_DEPEND_ON_BACKTEST_ENGINE = noClasses()
            .that()
            .implement(StrategyGenerator.class)
            .should()
            .dependOnClassesThat()
            .haveSimpleName("DeterministicBacktestEngine");

    @ArchTest
    static final ArchRule GENERATORS_DO_NOT_DEPEND_ON_EVALUATOR = noClasses()
            .that()
            .implement(StrategyGenerator.class)
            .should()
            .dependOnClassesThat()
            .haveSimpleName("DefaultExperimentEvaluator");

    @ArchTest
    static final ArchRule GENERATORS_DO_NOT_DEPEND_ON_RANKING = noClasses()
            .that()
            .implement(StrategyGenerator.class)
            .should()
            .dependOnClassesThat()
            .haveSimpleName("DefaultRankingService");

    @ArchTest
    static final ArchRule PIPELINE_DOES_NOT_DEPEND_ON_CONCRETE_SEARCH_GENERATORS = noClasses()
            .that()
            .haveSimpleName("BacktestWorkerService")
            .or()
            .haveSimpleName("DeterministicBacktestEngine")
            .or()
            .haveSimpleName("DefaultExperimentEvaluator")
            .or()
            .haveSimpleName("DefaultRankingService")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("RandomStrategyGenerator")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleName("GeneticStrategyGenerator");
}
