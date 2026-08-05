# zia-rag
Micro service pour exposer des fonctionnalités de Retrieval d’un Vector Store.

## Endpoints REST

Base path : `/api/v1/retrieval`

| Méthode | Chemin | Description |
|---------|--------|-------------|
| `POST`  | `/documents` | Retourne les `Document` (objets Spring AI) les plus pertinents pour une question. |
| `GET`   | `/sources`   | Liste les valeurs distinctes de la métadonnée `source`. |

### Corps de la requête `POST /documents`

```json
{
  "question": "Comment réinitialiser mon mot de passe ?",
  "config": {
    "topK": 10,
    "useReranker": false,
    "useBm25": false,
    "sources": ["handbook", "faq"]
  }
}
```

- `topK` : nombre maximum de documents retournés (défaut : 10).
- `useReranker` : active le reranking distant.
- `useBm25` : ajoute une recherche plein-texte BM25 (extension PG `pg_textsearch`,
  index `idx_vector_store_content_bm25`) à la recherche vectorielle classique.
  Comme les scores des deux retrievers ne sont pas comparables, activer `useBm25`
  **force** l'utilisation du reranker. Les résultats sont alors unis puis dédupliqués
  par identifiant de document avant reranking.
- `sources` : filtre optionnel sur la métadonnée `source` (vide = pas de filtre).

## Documentation Swagger

L'API est documentée via springdoc-openapi :
- UI : `/swagger-ui.html`
- Spécification OpenAPI : `/v3/api-docs`

## Configuration

Variables d'environnement principales (voir `application.yml`) :

| Variable | Description |
|----------|-------------|
| `DS_URL`, `DS_USERNAME`, `DS_PASSWORD` | Datasource PostgreSQL (pgvector + `pg_textsearch`). |
| `PGVECTOR_SCHEMA_NAME`, `PGVECTOR_TABLE_NAME` | Schéma / table du vector store. |
| `ZIA_EMBEDDING_*` | Modèle d'embedding (base-url, api-key, model, dimensions). |
| `ZIA_RERANK_BASE_URL`, `ZIA_RERANK_API_KEY`, `ZIA_RERANK_MODEL` | Service de reranking distant. |

> **Note** : le contrat exact de l'API de reranking n'est pas encore figé. Le mapping
> requête/réponse est isolé dans `RemoteReranker` (voir les `TODO`) et devra être adapté.

