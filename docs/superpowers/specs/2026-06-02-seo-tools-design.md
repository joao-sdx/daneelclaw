# SEO Tools Design

**Date:** 2026-06-02  
**Project:** daneelclaw  
**Status:** Approved

## Overview

Two new Daneel AI tools that expose DataForSEO's Google News search and content-parsing APIs to the LLM. The search tool returns a list of articles; the fetch tool downloads one article's content and saves it as a sandboxed `.md` file.

---

## Components

All new code lives under `org.daneel.tool.seo` in daneelclaw.

| Class | Role |
|---|---|
| `DataForSeoProperties` | `@ConfigurationProperties` record — `user` and `key` credentials |
| `DataForSeoClient` | `@Component` — wraps the two DataForSEO HTTP endpoints |
| `NewsArticle` | Record — `resultId, title, url, domain, published` |
| `SeoSearchTool` | `@Component` DaneelTool — `seo_search` |
| `SeoFetchArticleTool` | `@Component` DaneelTool — `seo_fetch_article` |

### Configuration (`application.yml`)

```yaml
daneel.tools.dataforseo.api:
  user: ${DATAFORSEO_USER}
  key: ${DATAFORSEO_KEY}
```

Credentials are injected via environment variables. No defaults — missing vars cause startup failure with a clear message.

---

## Tool 1: `seo_search`

### Parameters

| Name | Required | Default | Description |
|---|---|---|---|
| `keyword` | yes | — | Search term |
| `language` | no | `en` | Language code |
| `location` | no | `United States` | Location name |
| `depth` | no | `10` | Max number of results to return |

### Behaviour

Calls `POST https://api.dataforseo.com/v3/serp/google/news/live/advanced` with the given parameters. Parses the response and extracts the `result_id` field alongside title, url, domain, and published date for each result.

### Return value

JSON array (as a String):

```json
[
  {
    "result_id": "abc123",
    "title": "Article Title",
    "url": "https://example.com/article",
    "domain": "example.com",
    "published": "2024-01-15"
  }
]
```

Returns `"Error: keyword is required"` if keyword is blank.  
Returns `"Error: DataForSEO search failed: {message}"` on API or parse failure.

---

## Tool 2: `seo_fetch_article`

### Parameters

| Name | Required | Default | Description |
|---|---|---|---|
| `result_id` | yes | — | Article ID from `seo_search` results |
| `url` | yes | — | Article URL to fetch content from |
| `keyword` | no | — | If provided, stored in YAML frontmatter |
| `return_text` | no | `false` | If `true`, returns article text in the response |

### Behaviour

1. Calls `POST https://api.dataforseo.com/v3/on_page/content_parsing/live` with the given `url`.
2. Extracts the markdown-formatted article text from the response.
3. Builds a `.md` file with YAML frontmatter.
4. Saves it to `{sandbox_root}/{result_id}.md` via `SandboxFileSystem`.
5. Returns `"Saved to {result_id}.md"`, or the full file content (frontmatter + text) if `return_text=true`.

### Saved file format

```markdown
---
result_id: abc123
title: "Article Title"
url: https://example.com/article
domain: example.com
published: "2024-01-15"
keyword: optional search term
---

Article content here...
```

The `keyword` field is omitted from the frontmatter if not provided.

### Error cases

- Missing `result_id` or `url` → `"Error: {field} is required"`
- Path traversal attempt → `SandboxAccessException` caught → `"Error: access denied"`
- API failure or unparseable content → `"Error: failed to fetch article: {message}"`

---

## DataForSeoClient

Thin `@Component` wrapping two API calls using Java's `HttpClient`:

- `List<NewsArticle> searchNews(String keyword, String language, String location, int depth)`
- `String fetchContent(String url)` → returns markdown text

Uses HTTP Basic Auth (`Base64(user:key)`). Throws a checked exception on non-2xx responses or JSON parse failure; callers catch and return an `"Error: ..."` string.

No unit test for the client itself — it is a thin HTTP wrapper. Both tools mock it entirely in tests.

---

## Testing

### `SeoSearchToolTest`

- Happy path: mock client returns two articles → verify JSON output shape
- Missing `keyword` → verify `"Error: keyword is required"`
- Client throws → verify error string returned

### `SeoFetchArticleToolTest`

- Happy path: mock client returns content → verify `.md` written with correct YAML frontmatter
- `return_text=true` → verify article body included in response
- Missing `result_id` → verify error
- Missing `url` → verify error
- `SandboxAccessException` thrown by `SandboxFileSystem` → verify `"Error: access denied"`
- Client throws → verify error string

---

## Out of scope

- Batch/bulk fetching (handled by the existing `seo-news-search` Spring Batch pipeline)
- Result caching between `seo_search` and `seo_fetch_article` calls
- Pagination of search results beyond the `depth` parameter
