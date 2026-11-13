# AGENTS.md — zia-rag

Microservice de **retrieval** sur le vector store ZIA (pgvector + BM25 `pg_textsearch`, reranker distant).
API REST sous `/zia-rag/api/v1/retrieval` : voir `README.md` (endpoints, modes `VECTOR` / `BM25` /
`HYBRID` / `FILTER`, filtres de métadonnées, variables d'environnement, sécurité).

## Stack

- **Java 25**, **Spring Boot 4.1.x**, **Spring AI 2.0.x** (pgvector, embeddings OpenAI-compatibles), **Maven**.
- springdoc-openapi (`/zia-rag/swagger-ui.html`), configuration dans `src/main/resources/application.yml`.
- Package racine : `zas.admin.zia.rag` — `web/` (controllers + `dto/`), `retrieval/`, `rerank/` (+ `dto/`),
  `config/` (+ `properties/`), `logging/`.

## Build & tests

- Validation de référence : `mvn clean verify` (CI GitHub Actions).
- Un seul test : `mvn -o test -Dtest=NomDeLaClasseTest`.
- Profil Maven `internal` : ajoute `zas-common-security-starter` (validation du jeton bleu + groupes RHDS).
  Utilisé par le `Jenkinsfile` (`mvn -B clean verify -Pinternal`), indisponible sur GitHub.
  Sans ce profil, toutes les requêtes sont autorisées (dev local, CI GitHub).
- Requêtes HTTP de test : `src/main/resources/retrieve.http` ; l'environnement privé
  `http-client.private.env.json` n'est **jamais** committé.

## Conventions de code

- **Pas de Lombok.** Records pour les DTOs ; injection par constructeur.
- Propriétés de configuration typées (`config/properties`, `@ConfigurationProperties`).
- Seuls les documents publics (métadonnée `user_uuid` absente ou vide) sont retournés : ne jamais
  relâcher ce filtre. Les clés réservées (`zia.rag.retrieval.reserved-metadata-keys`) restent non filtrables.
- Toute évolution de l'API doit être reflétée dans `README.md` et dans l'OpenAPI ; conserver la
  compatibilité des champs dépréciés (`useBm25`, `sources`) tant qu'ils sont documentés.
- Tests obligatoires pour toute nouvelle fonctionnalité.

## Git

- Base d'intégration : `develop` ; production : `master`. Jamais de commit direct sur ces branches.
- Branches de travail : `feature/<nom>` (ou `chore/…`, `bugfix/…`) depuis `develop`, PR vers `develop`.
- Release : `release/<version>` depuis `develop` vers `master`.
- Messages de commit au format Conventional Commits.
