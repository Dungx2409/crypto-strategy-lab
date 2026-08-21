# Next tasks

## Current verification

- [x] Record the final `mvn clean verify` rerun after the search lifecycle correction.
- [x] Run `scripts/verify-architecture-proofs.sh` with PostgreSQL and RabbitMQ Testcontainers.
- [ ] Open the running dashboard in a connected browser at 1680 by 945 and compare each core screen with the supplied images.
- [ ] Check responsive behavior at tablet and mobile widths.
- [x] Run one complete search with the API and worker containers and inspect the public leaderboard and experiment APIs.

## Remaining architecture work

- [ ] Feed evaluated fitness back into the Genetic generator. Current crossover varies membership but parent selection still uses deterministic structure rather than backtest score.
- [ ] Add an automated browser test for navigation, independent chart changes, and trade selection when a browser runner is available.

## Optional extensions outside the written MVP

- [ ] Prompt or URL strategy authoring through an LLM, with schema validation, versioning, and a safe execution model.
- [ ] Self-healing LLM news extraction with versioned templates and review controls.
- [ ] Add a versioned SentimentStrategy plugin if the course demo chooses to include news as a trading signal.
- [ ] Add 30m, 2h, and 1d after the five required demo timeframes are stable.
