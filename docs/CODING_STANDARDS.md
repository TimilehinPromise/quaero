# Coding Standards — know-commute

Binding for all code in this repository. Reviewers reject on these.

## Comments

The rule that matters most here: **comments explain _why_, never _what_.**

- If a comment restates what the code already says, delete it.
- If code needs a comment to be readable, rename the thing instead. A well-named method beats
  a comment above a badly-named one.
- Legitimate comments: a non-obvious constraint, a workaround with a link to the upstream
  issue, a deliberate deviation from the obvious approach, a unit that isn't in the name.
- Banned: banner/separator comments, commented-out code, `// TODO` without a ticket
  reference, Javadoc that repeats the signature, "// getters and setters below".

```java
// Bad — restates the code
// increment the attempt counter
bookmark.setAttempts(bookmark.getAttempts() + 1);

// Good — explains a decision the code cannot express
// Re-validated per redirect hop: DNS can rebind between the initial check and the fetch.
urlValidator.validate(redirectTarget);
```

Javadoc goes only on public API with a contract you cannot infer from the signature —
nullability, side effects, thread safety, units, or ownership of a returned resource.

## Structure

- **Package by feature**, not by layer: `bookmark/`, `pipeline/`, `extraction/`, `summary/`,
  `tts/`, `storage/`, `digest/`, `push/`. No top-level `controllers/`, `services/`, `dtos/`.
- Entities never cross the controller boundary. Map to records at the edge.
- No business logic in controllers. No HTTP types (`ResponseEntity`, `HttpHeaders`) in
  services.
- One public type per file.

## Java

- Records for DTOs, requests, responses, and value objects.
- Constructor injection only. No field `@Autowired`, no setter injection.
- `Optional` as a return type only — never a field, parameter, or collection element.
- Prefer `List.of` / `Map.of` and immutable collections. Return empty, never null.
- `var` where the right-hand side already names the type; explicit type otherwise.
- Lombok is limited to `@RequiredArgsConstructor`, `@Getter`, `@Builder`.
  **Never `@Data` or `@EqualsAndHashCode` on a JPA entity** — generated `equals`/`hashCode`
  over mutable fields breaks identity semantics and triggers lazy loads.

## Persistence

- Liquibase owns the schema. Hibernate is `ddl-auto=validate` and must stay that way.
- Changesets are immutable once merged. Fix forward with a new changeset; never edit an
  applied one.
- Filename order is the apply order: `NNN-description.yaml`.
- Enum-valued columns are `varchar` with a check constraint, not Postgres enum types.
- `spring.jpa.open-in-view=false` stays off. Fetch what you need in the service.

## External calls

- Every outbound call sets an explicit connect and read timeout. No exceptions.
- No unbounded retries. Bounded attempts with backoff, then a terminal failure state.
- **Never make an external call inside a database transaction.** A 60s TTS call holding a
  pooled connection exhausts the pool at trivial load.
- Secrets come from environment variables. Nothing secret enters `application.properties` or
  git.

## Errors

- `@RestControllerAdvice` returning RFC 9457 `ProblemDetail`. No stack traces or internal
  identifiers in responses.
- Catch narrowly. A bare `catch (Exception e)` needs a comment justifying it — this is one of
  the few places a comment is expected.
- Log the cause once, at the boundary that handles it. Do not log-and-rethrow.

## Tests

- Test names describe behaviour, not methods: `rejectsPrivateNetworkUrls`, not
  `testValidate`.
- **No network in tests.** External HTTP is `MockWebServer`; article extraction runs against
  saved fixtures in `src/test/resources/fixtures/`.
- Integration tests use the existing Testcontainers setup (`TestcontainersConfiguration`).
  Real Postgres, never H2 — we depend on Postgres-specific behaviour (`FOR UPDATE SKIP
  LOCKED`, partial unique indexes).
- Tests that need real credentials are `@Disabled` with a comment naming the env var
  required to run them.
