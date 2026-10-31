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

MVC validation tests disable filters only in their test configuration and register
test-only endpoints; security configuration tests run with the real filters enabled.

# Security baseline

Only GET `/api/v1/auth/csrf` is permitted anonymously. All other routes are denied, including for authenticated callers,
until explicit permissions are added with each feature. Form login, HTTP Basic,
default logout, and saved-request caching are disabled. CSRF stays enabled.

Security failures use the same `application/problem+json` structure as controller
errors, via dedicated filter handlers:

| HTTP status | Code | Meaning |
| --- | --- | --- |
| 401 | `AUTHENTICATION_REQUIRED` | Authentication is required. |
| 403 | `ACCESS_DENIED` | An authenticated caller lacks route permission. |
| 403 | `CSRF_INVALID` | The CSRF token is missing or invalid, even for an anonymous caller. |

Responses have `Cache-Control: no-store`, contain no request values or exception
details, and do not redirect to HTML login/error pages. A valid CSRF token does not
grant authorization; after passing CSRF, an anonymous write to an unlisted route
receives 401, while an authenticated write receives 403.

GET `/api/v1/auth/csrf` returns HTTP 200 with `Cache-Control: no-store`:

```json
{"token": "generated-request-token", "header_name": "X-CSRF-TOKEN"}
```

Retain the session cookie returned by the server and send it with the token in the
named header on POST/PATCH/DELETE requests. Tokens from another session, or sent
without the original session, fail. Fetching a token does not log in the caller or
grant route permission. Re-fetching in an existing session does not invalidate its
previously returned tokens. Spring masks request tokens, so their strings can
differ between fetches even when they correspond to the same stored session token.

Sessions currently use servlet memory: an app restart or expired session requires
a new token and session. JDBC sessions remain deferred until their tables are
migrated. Fetch a fresh token after login/logout once those features are added.
For local HTTP clients, set `SESSION_COOKIE_SECURE=false`; retain secure cookies
for production HTTPS. Keep CSRF tokens and cookies out of logs and URLs.

Manual local check (application running on port 8080):

```sh
curl -i -c /tmp/learningq-dev-cookies.txt http://localhost:8080/api/v1/auth/csrf
```

The cookie file contains session credentials; remove it when finished. No write
endpoint is authorized yet. The tests submit the returned token to a denied route:
401 after passing CSRF demonstrates token acceptance, while missing/wrong-session
tokens produce 403 `CSRF_INVALID`. No successful signup or login is implied.

Run the API and unit tests without database credentials:

```sh
./mvnw -Dtest=SecurityConfigTests,ApiExceptionHandlerTests,UserTests,EmailNormalizerTests test
```

For full verification including MariaDB repository tests, set the development/test
database environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) and run
`./mvnw verify`.

# Password encoding and policy

Use `PasswordService.encodeNewPassword(rawPassword)` for new or changed passwords,
and `PasswordService.matches(rawPassword, storedHash)` for verification. Never pass
raw passwords to the user entity or repository. Signup/login endpoints do not yet
exist; this milestone provides the components they will use.

New passwords must contain 12 to 128 Unicode code points. The 12-character minimum
is a product tradeoff for lower signup friction; it is below NIST's 15-character
minimum for password-only authentication. Spaces, punctuation and
Unicode are accepted without uppercase/digit/symbol requirements. No trimming,
case conversion, Unicode normalization or truncation occurs. Malformed Unicode is
rejected to prevent different inputs being converted to replacement characters.
Verification bounds input size but does not reapply the current minimum length,
so a future minimum-length increase need not lock out older accounts.

`PasswordConfig` registers Spring Security's `Argon2PasswordEncoder` using Argon2id
with a 16-byte random salt, 32-byte hash, 19 MiB memory, two iterations and one lane.
The stored PHC string includes the algorithm, version, parameters and salt. It fits
the existing `password_hash VARCHAR(255)` column; no migration is required. Encoding
the same password twice produces independently salted hashes.

Bouncy Castle `bcprov-jdk18on` 1.86 supplies Argon2's runtime implementation. Its
version is pinned explicitly because this Boot parent does not manage it.

References: [Spring password storage](https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html),
[OWASP Argon2id guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html),
[Bouncy Castle releases](https://www.bouncycastle.org/download/bouncy-castle-java/).
Benchmark hashing on the intended deployment hardware before public release.

Deferred to milestone 23: choose and implement a compromised-password checker
before exposing account registration publicly. Length acceptance alone does not
establish password strength or detect common/compromised passwords.

Password tests run without database variables:

```sh
./mvnw -Dtest=PasswordPolicyTests,PasswordServiceTests test
```
