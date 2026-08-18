# ADR-006: Modular Monolith plus Worker Process

## Context

The project must demonstrate boundaries and worker scaling while remaining
understandable and operable by a university team.

## Decision

Keep business contexts in one core module, adapters in infrastructure, and run
API/worker as separate Spring Boot processes from one repository.

## Alternatives

Immediate microservices; one undifferentiated Spring Boot application.

## Consequences

Transactions and local development stay simple. Package/module rules are needed
to prevent boundary erosion.

## Evidence

The Maven reactor, Enforcer, and automated ArchUnit rules enforce the selected
dependency direction. M7 adds dashboard/status adapters in `api-app` and metrics
and persistence adapters in `infrastructure`; `core` remains free of Spring MVC,
JPA, RabbitMQ, and provider-specific types. Compose runs API and worker as
separate processes from the same repository.
