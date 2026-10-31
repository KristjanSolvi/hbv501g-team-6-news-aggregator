# Team 6 News Aggregator

A Java Spring Boot backend for HBV501G Software Project 1.

## Weeks 1–2

- Week 1: domain entities, database relationships, and controller/service/repository layers.
- Week 2: UC1 View News Feed, UC4 Register Account, and UC5 Log In.

UC2, UC6, UC7, UC9, UC10 and UC18 are also implemented.

## Run

Requires Java 17 or newer. The Maven wrapper downloads Maven and dependencies on its first run.
Run these commands from `website`:

```sh
./mvnw spring-boot:run
```

The API runs at `http://localhost:8080`. H2 stores data in `data/newsaggregator-uuid.mv.db`.
The old numeric-ID database is left untouched; its data is not migrated automatically.
Data survives restarts; the initial feed is empty. No separate database installation is needed.

For three fictional sample articles, use the optional demo profile:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Demo mode uses a separate, temporary database that resets on restart.

```sh
./mvnw test
```

Tests use their own in-memory database and cover registration, login sessions, CSRF,
pagination, article retrieval, source relationships, duplicate favourites, and administrator
account creation and updates, including authorization and rollback on conflicting emails.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/articles?page=0&size=20` | Public news feed |
| GET | `/api/articles/{id}` | Public article details |
| GET | `/api/auth/csrf` | Obtain a token for POST and PUT requests |
| POST | `/api/auth/register` | Register with JSON fields `email` and `password` |
| POST | `/api/auth/login` | Log in with form fields `email` and `password` |
| GET | `/api/accounts/me` | View own account |
| PUT | `/api/accounts/me` | Update own email and optional password |
| POST | `/api/favourites/{articleId}` | Save a favourite |
| GET | `/api/favourites?page=0&size=20` | List own favourites |
| POST | `/api/admin/accounts` | Administrator creates a new administrator account |
| PUT | `/api/admin/accounts/{id}` | Administrator updates an existing account |

All account, article, source and favourite IDs are UUIDs, returned as strings.

The feed starts at page 0, accepts sizes from 1 to 100, and sorts newest first.
Article IDs break ties when publication times match. Responses contain `content` (articles)
and `page` (`number`, `size`, `totalElements`, `totalPages`). Each article includes its
source when available. Empty feeds and pages past the end return HTTP 200 with empty content.
Invalid pagination returns 400.

Article details include the full content, source information when available, and the owner ID
when present. The identifier must be a UUID; invalid identifiers return 400 and
unknown articles return 404. Reading an article does not require login.

Registration returns 201 with the account's ID, email, and `USER` role. Emails are
case-insensitive and unique; duplicates return 409. Passwords must be 8–128 characters
and cannot be blank. Invalid registration returns 400. Only salted password hashes are stored;
neither passwords nor hashes appear in API responses. Registration does not log the user in.

Login uses Spring Security's form handling and returns 204 with a session cookie.
Invalid credentials return 401. Keep the cookie for subsequent requests; sessions expire
after 30 minutes of inactivity. CSRF tokens protect POST and PUT requests against requests forged
by other websites. Missing or invalid tokens return 403. Fetch a new token after login.

Administrator account creation requires an existing administrator session and a CSRF token.
Send JSON with `email` and `password`, using the same validation rules as registration.
Success returns 201 with the new account's ID, normalized email, and `ADMIN` role. Duplicate
emails return 409; invalid input returns 400. Public registration still creates only `USER`
accounts. The new administrator can log in through the existing login endpoint.

Administrator account updates require an administrator session and a CSRF token. Send JSON with a required
`email` and an optional `password`; omitting the password or sending `null` preserves the
existing password. A supplied password follows the registration rules. Successful updates
return 200 with the account's ID, normalized email, and unchanged role. Invalid input returns
400, unknown accounts return 404, and duplicate emails return 409 without changing the account.
Unauthenticated requests with a valid CSRF token return 401; regular users receive 403.

### Try it with curl

These examples use Python 3 to read the CSRF token from JSON. Use an article UUID from your feed:

```sh
curl 'http://localhost:8080/api/articles?page=0&size=2'
curl 'http://localhost:8080/api/articles/9491db8b-175a-4920-9ee8-d3fb454398c0'

cookies=$(mktemp)
token=$(curl -s -c "$cookies" http://localhost:8080/api/auth/csrf |
  python3 -c 'import json, sys; print(json.load(sys.stdin)["token"])')

curl -i -b "$cookies" -c "$cookies" \
  -H "X-CSRF-TOKEN: $token" -H 'Content-Type: application/json' \
  -d '{"email":"reader@example.com","password":"example-password"}' \
  http://localhost:8080/api/auth/register

curl -i -b "$cookies" -c "$cookies" -H "X-CSRF-TOKEN: $token" \
  --data-urlencode 'email=reader@example.com' \
  --data-urlencode 'password=example-password' \
  http://localhost:8080/api/auth/login
```

In Postman, keep cookies enabled. First GET `/api/auth/csrf`, then copy its `token`
into the `X-CSRF-TOKEN` header for POST and PUT requests. Use a raw JSON body for registration
and an `x-www-form-urlencoded` body for login.

## Code layout

All Java packages are under `src/main/java/is/hi/team6/newsaggregator`:

- `model`: `UserAccount`, `Article`, `NewsSource`, and `Favourite` map to database tables.
  Articles have optional source and owner references. Favourites link one account to one
  article, with a database constraint preventing duplicate pairs.
- `repository`: Spring Data JPA interfaces provide database access.
- `service`: feed ordering, article retrieval, account creation and updates, and account lookup
  for login. `ArticleService` and `AccountService` have corresponding `Impl` classes.
- `controller`: request validation and API routes. Spring Security handles the login POST.
- `dto`: small records defining request and response data without exposing entire entities.
- `config`: password hashing, session login, public routes, and authentication errors.

Start reading with `ArticleController` → `ArticleServiceImpl` → `ArticleRepository`, then
`AuthController` / `AdminAccountController` → `AccountServiceImpl` → `SecurityConfig`.

Authentication currently uses sessions.
The team's JWT decision can be revisited with the later security material.

Implementation references: [Spring Security form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
and [CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## API Documentation

Interactive API documentation is available when the application is running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

The documentation is generated automatically from the REST controllers.

Note: `POST /api/auth/login` is handled by Spring Security rather than a REST
controller, so it is not automatically discovered by OpenAPI.

## Team

- Alexander Viðar Garðarsson
- Kristján Sölvi Örnólfsson
- Kristján Jakob Ásgrímsson
- Stefán Steinar Guðlaugsson
