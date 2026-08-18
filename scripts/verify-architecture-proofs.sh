#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

echo "[proof] Binance reconnect/gap/duplicate; News isolation; MACD; Random/Genetic; worker 1-to-3; Top #1 provenance"

# One clean reactor invocation keeps inter-module classpaths reproducible while
# Surefire and Failsafe select only the explicit architecture-proof tests.
mvn clean verify \
  -Dtest=MarketDataStreamServiceTest,NewsFailureIsolationTest,M7DashboardTest,StrategyExtensionArchitectureTest,GeneratorReplacementArchitectureTest,ArchitectureRulesTest \
  -Dit.test=BacktestWorkerIT,ExperimentPipelineIT,MarketDatasetMaterializationIT,CandleStoreIT \
  -Dsurefire.failIfNoSpecifiedTests=false
