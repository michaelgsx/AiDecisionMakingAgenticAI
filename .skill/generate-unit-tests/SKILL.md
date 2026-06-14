---
name: generate-unit-tests
description: >-
  Generate JUnit 5 unit tests for Spring Boot Java classes in AiDecisionMakingAgenticAI.
  Use when the user asks for unit tests, test coverage, or test cases for orchestrator,
  services, tools, or controllers under backend/src/main/java.
---

# Generate unit tests (AiDecisionMakingAgenticAI)

## Scope

- **Repo root:** `AiDecisionMakingAgenticAI/`
- **Source:** `backend/src/main/java/com/aidecision/agentic/`
- **Tests:** `backend/src/test/java/com/aidecision/agentic/` (mirror package layout)
- **Runner:** JUnit 5 + Spring Boot Test + AssertJ (from `spring-boot-starter-test`)

## Workflow

1. Read the class under test and its public methods; list branches and edge cases.
2. Place test class at `backend/src/test/java/.../<ClassName>Test.java` (same package as source).
3. Prefer **pure unit tests** (no Spring context) unless `@WebMvcTest` / `@DataJpaTest` is clearly needed.
4. Name tests `methodName_scenario` (e.g. `evaluate_numericComparison`).
5. Use `@BeforeEach` for shared setup; avoid unnecessary `@SpringBootTest` (slow).
6. Assert with AssertJ: `assertThat(...).isEqualTo()`, `.isTrue()`, `.contains()`, etc.
7. Mock collaborators with Mockito (`@Mock`, `@InjectMocks`, or manual stubs) — do not hit Azure OpenAI or SQL in unit tests.
8. Run: `cd backend && mvn -q test -Dtest=<ClassName>Test` (or full `mvn test` if requested).

## Conventions (this repo)

```java
package com.aidecision.agentic.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleServiceTest {

    private ExampleService service;

    @BeforeEach
    void setUp() {
        service = new ExampleService(/* deps */);
    }

    @Test
    void doWork_happyPath() {
        assertThat(service.doWork("input")).isEqualTo("expected");
    }
}
```

## What to test

| Layer | Focus |
|-------|--------|
| `orchestrator/` | DAG validation, gate conditions, planner output parsing, merge/dedup logic |
| `service/` | Business rules, error mapping, status transitions (mock repos) |
| `tool/impl/` | Request/response shaping, guard rails (mock HTTP/LLM) |
| `controller/` | `@WebMvcTest` + MockMvc for HTTP contract only when asked |

## Do not

- Add tests that require live Azure OpenAI, SQL Server, or Key Vault.
- Commit `backend/.env` or secrets.
- Generate trivial tests that only assert constants with no behavior.

## Verify

```bash
cd backend && mvn -q test
```

Fix failures before finishing; only add tests that meaningfully cover real behavior.
