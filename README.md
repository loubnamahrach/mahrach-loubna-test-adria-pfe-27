# Plateforme bancaire distribuee et detection de fraude

Test technique ENSET - ADRIA (Master SDIA) - depot `mahrach-loubna-test-adria-pfe-27`.

## Modules Maven

| Module | Role |
|---|---|
| discovery-service | Eureka Server |
| config-service | Spring Cloud Config Server |
| account-service | Gestion des comptes (REST, GraphQL, MCP) |
| transaction-service | Gestion des transactions et virements (REST, GraphQL, MCP) |
| gateway-service | Spring Cloud Gateway (reactive) |
| chat-bot-service | Agent conversationnel Spring AI |

Dossiers complementaires : `angular-client/`, `fraud-detection/`, `docker/`, `.github/workflows/`.

## Prerequis

Java 21, Maven 3.9+. Versions : Spring Boot 3.5.16, Spring Cloud 2025.0.3, Spring AI 1.1.8.

## Compilation

`mvn clean verify` (depuis la racine du projet).