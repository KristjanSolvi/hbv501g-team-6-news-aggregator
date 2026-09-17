# Team 6 News Aggregator

A Java Spring Boot backend for HBV501G Software Project 1.

## Weeks 1–2

- Week 1: domain entities, database relationships, and controller/service/repository layers.
- Week 2: UC1 View News Feed, UC4 Register Account, and UC5 Log In.

## Run

Requires Java 17 or newer. The Maven wrapper downloads Maven and dependencies on its first run.
Run these commands from `website`:

```sh
./mvnw spring-boot:run
```

The API runs at `http://localhost:8080`. H2 stores data in `data/`, which Git ignores.
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
pagination, source relationships, and duplicate favourites.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/articles?page=0&size=20` | Public news feed |
| GET | `/api/auth/csrf` | Obtain a token for POST requests |
| POST | `/api/auth/register` | Register with JSON fields `email` and `password` |
| POST | `/api/auth/login` | Log in with form fields `email` and `password` |

The feed starts at page 0, accepts sizes from 1 to 100, and sorts newest first.
Article IDs break ties when publication times match. Responses contain `content` (articles)
and `page` (`number`, `size`, `totalElements`, `totalPages`). Each article includes its
source when available. Empty feeds and pages past the end return HTTP 200 with empty content.
Invalid pagination returns 400.

Registration returns 201 with the account's ID, email, and `USER` role. Emails are
case-insensitive and unique; duplicates return 409. Passwords must be 8–128 characters
and cannot be blank. Invalid registration returns 400. Only salted password hashes are stored;
neither passwords nor hashes appear in API responses. Registration does not log the user in.

Login uses Spring Security's form handling and returns 204 with a session cookie.
Invalid credentials return 401. Keep the cookie for subsequent requests; sessions expire
after 30 minutes of inactivity. CSRF tokens protect POST requests against requests forged
by other websites. Missing or invalid tokens return 403. Fetch a new token after login.

### Try it with curl

These examples use Python 3 to read the CSRF token from JSON:

```sh
curl 'http://localhost:8080/api/articles?page=0&size=2'

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
into the `X-CSRF-TOKEN` header for POST requests. Use a raw JSON body for registration
and an `x-www-form-urlencoded` body for login.

## Code layout

All Java packages are under `src/main/java/is/hi/team6/newsaggregator`:

- `model`: `UserAccount`, `Article`, `NewsSource`, and `Favourite` map to database tables.
  Articles have optional source and owner references. Favourites link one account to one
  article, with a database constraint preventing duplicate pairs.
- `repository`: Spring Data JPA interfaces provide database access.
- `service`: feed ordering, account creation, and account lookup for login.
- `controller`: request validation and API routes. Spring Security handles the login POST.
- `dto`: small records defining request and response data without exposing entire entities.
- `config`: password hashing, session login, public routes, and authentication errors.

Start reading with `ArticleController` → `ArticleService` → `ArticleRepository`, then
`AuthController` → `AccountService` → `SecurityConfig`.

Source, ownership, and favourite relationships are the week 1 database foundation;
their management endpoints belong to later weeks. Authentication currently uses sessions.
The team's JWT decision can be revisited with the later security material.

Implementation references: [Spring Security form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
and [CSRF protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## Team

- Alexander Viðar Garðarsson
- Kristján Sölvi Örnólfsson
- Kristján Jakob Ásgrímsson
- Stefán Steinar Guðlaugsson
