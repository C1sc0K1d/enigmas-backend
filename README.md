# enigmas-backend

REST API for the Enigmas project, built with Java 21, Spring Boot, Maven, and PostgreSQL.

## Requirements

- JDK 21, with JAVA_HOME pointing to the JDK directory and its bin directory on PATH.
- A running PostgreSQL instance.
- Internet access for the first Maven Wrapper execution.

Maven is provided through the wrapper; a separate Maven installation is not required.

## Local setup

Create the database using pgAdmin or psql:

```sql
CREATE DATABASE enigmas;
```

Configure these environment variables in your IDE run configuration:

| Variable | Local value | Purpose |
| --- | --- | --- |
| SPRING_PROFILES_ACTIVE | dev | Enables automatic schema updates for local development |
| DB_URL | jdbc:postgresql://localhost:5432/enigmas | JDBC connection URL (this is the default) |
| DB_USERNAME | postgres | Database user (this is the default) |
| DB_PASSWORD | Your local database password | Required database password |
| PORT | 8080 | Optional HTTP port |

Run `br.com.enigmas.backend.EnigmasBackendApplication` from your IDE.

Alternatively, in PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/enigmas'
$env:DB_USERNAME = 'postgres'
$secret = Read-Host 'PostgreSQL password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
.\mvnw.cmd spring-boot:run
```

The server listens on http://localhost:8080. There are no API endpoints yet; a 404 at `/` is expected.

Spring Boot does not automatically load `.env` files. Set environment variables in the IDE or shell. Do not commit passwords.

## Tests and build

```powershell
.\mvnw.cmd verify
```

The application context smoke test uses a separate in-memory H2 database and the `test` profile. It does not require PostgreSQL or credentials, and does not validate PostgreSQL-specific behavior. H2 is a test-only dependency.

The executable JAR is generated under `target/`.

## Database schema

The default configuration uses `ddl-auto=validate`, which does not modify the database schema. The `dev` profile uses `update` for local development only. Add versioned database migrations before deploying to production, and do not enable the `dev` profile there.

## Package structure

The project uses **package by feature**, with layers inside each feature. This is the project convention; Spring does not require a single folder layout.

```text
src/main/java/br/com/enigmas/backend/
|-- EnigmasBackendApplication.java
|-- config/                 # Application-wide Spring configuration
|-- shared/
|   `-- exception/          # Shared exceptions and centralized HTTP error handling
`-- enigma/                 # Enigma feature
    |-- controller/         # HTTP endpoints
    |-- dto/                # Request and response contracts
    |-- service/            # Business operations and transaction boundaries
    |-- entity/             # JPA entities
    `-- repository/         # Database access

src/main/resources/
|-- application.properties
`-- application-dev.properties

src/test/java/br/com/enigmas/backend/
`-- EnigmasBackendApplicationTests.java

src/test/resources/
`-- application-test.properties
```

The feature packages currently contain only `package-info.java` documentation. Controllers, business rules, and persistence classes will be implemented as requirements are defined.

### Dependency rules

- Controllers delegate to services; they do not access repositories directly.
- Services own business operations and transaction boundaries, and use repositories for persistence.
- Request and response DTOs define the API contract. Do not return JPA entities directly from endpoints.
- Repositories contain persistence access, not business rules.
- Keep each feature's classes inside its own package. Create additional feature packages as needed, following the same structure.
- Keep `shared` small and limited to code actually shared by multiple features. Feature-specific exceptions remain inside their feature.
- Use English class and package names. Typical names are `EnigmaController`, `EnigmaService`, `EnigmaRepository`, `Enigma`, `CreateEnigmaRequest`, and `EnigmaResponse`.
- Add mapping classes inside the feature when mapping becomes complex; do not introduce empty service interfaces or generic base classes by default.
- Mirror production packages under `src/test/java` when adding tests. PostgreSQL-specific persistence behavior needs tests against PostgreSQL.
- Keep all application packages below `br.com.enigmas.backend` so Spring can discover them.

Future versioned SQL migrations belong in `src/main/resources/db/migration` once a migration tool is configured.
