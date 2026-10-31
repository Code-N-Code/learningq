# Database migrations

- Use filenames such as V1__create_users.sql.
- Separate the version and description with two underscores.
- Give each versioned migration a unique, increasing version.
- Never edit a migration already applied to a shared database.
- Make subsequent schema changes through new migrations.
- Write SQL compatible with MariaDB 11.8

# API validation and errors

Request and response DTO properties use snake_case JSON names. Unknown JSON
properties are rejected. Future controllers should accept dedicated DTOs using
`@Valid @RequestBody`; apply Jakarta constraints to the DTO fields. Do not expose
JPA entities or include credentials in DTO `toString()` implementations or logs.
Endpoint-specific DTOs arrive with their features; no signup endpoint exists yet.

Controller errors use `application/problem+json`, with standard `type`, `title`,
`status`, and `detail` fields plus a stable `code`. Validation failures also include
an `errors` array containing `field`, `code`, and a safe `message`:

```json
{
  "type": "urn:learningq:error:validation-failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "code": "VALIDATION_FAILED",
  "errors": [
    {"field": "email", "code": "INVALID_EMAIL", "message": "Enter a valid email address."}
  ]
}
```

Clients should use codes rather than parsing messages. Main codes are:

| Code | Meaning |
| --- | --- |
| `VALIDATION_FAILED` | Request-body or method-parameter constraints failed (400). |
| `MALFORMED_REQUEST` | Missing body, malformed JSON, invalid JSON types or unknown fields (400). |
| `INVALID_PARAMETER` | Request parameter could not be converted (400). |
| `REQUEST_FAILED` | Other framework request errors; the HTTP status and headers are preserved. |
| `INTERNAL_ERROR` | Server error; exception details are not exposed. |

Field codes are `REQUIRED`, `INVALID_EMAIL`, `INVALID_SIZE`, `INVALID_FORMAT`, and
`INVALID_VALUE`. Error messages come from a fixed allowlist rather than validator
messages, exception messages or rejected values. Future custom constraints use
`INVALID_VALUE` until explicitly assigned a safe public code/message.

Security-filter 401/403 responses are separate work for milestone 6. MVC tests
disable filters only in their test configuration and register test-only endpoints;
they do not change the application's security configuration.

Run the API and unit tests without database credentials:

```sh
./mvnw -Dtest=ApiExceptionHandlerTests,UserTests,EmailNormalizerTests test
```

For full verification including MariaDB repository tests, set the development/test
database environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) and run
`./mvnw verify`.
