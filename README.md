# zia-rag
Micro service pour exposer des fonctionnalités de Retrieval d’un Vector Store.

## Endpoints REST

Base path : `/zia-rag/api/v1/retrieval`

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
    "searchMode": "VECTOR",
    "useReranker": false,
    "metadataFilters": {
      "source": ["handbook", "faq"],
      "lang": ["fr"]
    },
    "contentContains": "mot de passe"
  }
}
```

- `topK` : nombre maximum de documents retournés (défaut : 10).
- `searchMode` : stratégie de recherche (défaut : `VECTOR`).
  - `VECTOR` : recherche vectorielle seule.
  - `BM25` : recherche plein-texte BM25 seule (extension PG `pg_textsearch`,
    index `idx_vector_store_content_bm25`).
  - `HYBRID` : les deux recherches, unies puis dédupliquées par identifiant de document.
    Comme leurs scores ne sont pas comparables, le reranker est **forcé**.
- `useReranker` : active le reranking distant (`VECTOR` et `BM25`).
- `metadataFilters` : filtres optionnels `clé → valeurs acceptées`. Un document est retenu si,
  pour **chaque** clé, sa métadonnée vaut **une** des valeurs (IN par clé, AND entre clés).
  Toute clé est acceptée sauf les clés réservées (`zia.rag.retrieval.reserved-metadata-keys`,
  défaut `user_uuid`) qui renvoient une `400`.
- `contentContains` : texte optionnel que le contenu doit contenir (sous-chaîne insensible à la
  casse, `ILIKE`), combiné en AND avec les filtres de métadonnées. En recherche vectorielle, la
  requête est alors exécutée directement en SQL sur la table pgvector (les filtres Spring AI ne
  supportent pas ce critère).
- Dépréciés (toujours supportés) :
  - `useBm25: true` ⇒ `searchMode: HYBRID` si `searchMode` est absent.
  - `sources` ⇒ fusionné dans `metadataFilters.source`.

> **Note** : seuls les documents publics sont retournés. Un document est considéré public
> lorsque sa métadonnée `user_uuid` est absente ou vide ; les documents rattachés à un
> utilisateur (`user_uuid` non vide) sont systématiquement exclus du retrieval.

## Documentation Swagger

L'API est documentée via springdoc-openapi :
- UI : `/zia-rag/swagger-ui.html`
- Spécification OpenAPI : `/zia-rag/v3/api-docs`

## Configuration

Variables d'environnement principales (voir `application.yml`) :

| Variable | Description |
|----------|-------------|
| `DS_URL`, `DS_USERNAME`, `DS_PASSWORD` | Datasource PostgreSQL (pgvector + `pg_textsearch`). |
| `PGVECTOR_SCHEMA_NAME`, `PGVECTOR_TABLE_NAME` | Schéma / table du vector store. |
| `ZIA_EMBEDDING_*` | Modèle d'embedding (base-url, api-key, model, dimensions). |
| `ZIA_RERANK_BASE_URL`, `ZIA_RERANK_API_KEY`, `ZIA_RERANK_MODEL` | Service de reranking distant. |
| `GATEWAY_PUBLIC_KEY` | Clé publique (Base64) de la gateway pour valider le jeton bleu (profil `internal`). |
| `ZIA_SECURITY_RETRIEVER_GROUPS` | Groupes RHDS (DN complets, séparés par `;`) autorisés à appeler l'API (profil `internal`). Vide = tout refusé. |
| `ZIA_RAG_RESERVED_METADATA_KEYS` | Clés de métadonnées non filtrables, séparées par `,` (défaut : `user_uuid`). |
| `ZIA_LOGGING_CALLER_ENABLED` | Active le log de l'appelant (username + authorities) pour chaque requête (défaut : `false`). |

## Sécurité

Spring Security est toujours présent. La validation du jeton bleu (header `Blue`) est fournie par
la librairie interne `zas-common-security-starter`, ajoutée uniquement via le profil Maven `internal`
(utilisé par le Jenkinsfile, non disponible sur GitHub) :

```bash
mvn clean verify -Pinternal
```

- Avec le profil `internal` : un jeton bleu valide est requis, sauf pour `/actuator/**`,
  `/swagger-ui.html`, `/swagger-ui/**` et `/v3/api-docs/**`. De plus, tous les endpoints de l'API
  (`@PreAuthorize` via le bean `RetrieverAccess`) exigent que l'appelant soit membre d'un des groupes
  de `zia.security.retriever-groups` (comparaison des DN insensible à la casse et aux espaces),
  sinon `403`.
- Sans le profil : toutes les requêtes sont autorisées (développement local, CI GitHub).

