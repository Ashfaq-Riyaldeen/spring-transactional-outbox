# Contributing to spring-transactional-outbox

Thanks for taking the time to contribute. Bug reports, documentation fixes, tests and code changes are all
welcome, and so are first-time contributors: if something in this guide is unclear, that is a bug in the guide.

## Contents

- [Your first contribution](#your-first-contribution)
- [Taking an issue](#taking-an-issue)
- [Setting up](#setting-up)
- [Project structure](#project-structure)
- [Building and testing](#building-and-testing)
- [Troubleshooting](#troubleshooting)
- [Conventions for every change](#conventions-for-every-change)
- [Conventions for the services' core](#conventions-for-the-services-core)
- [Pull requests and review](#pull-requests-and-review)
- [Reporting bugs and proposing changes](#reporting-bugs-and-proposing-changes)
- [Security issues](#security-issues)

## Your first contribution

1. Find an issue labelled `good first issue` and comment that you are taking it.
2. Fork the repository and set it up as described in [Setting up](#setting-up).
3. Run `./mvnw verify` once before changing anything, so you know the build works on your machine.
4. Make the change. Every issue says what is in scope and how to check the result; look at a neighbouring
   class or test and follow it.
5. Run `./mvnw verify` again and open a pull request with `Fixes #<issue number>` in the description.

Stuck? Ask in the issue. A question there is always better than a pull request that went in the wrong direction,
and nobody expects you to know the codebase.

## Taking an issue

- **Comment on the issue and it is yours.** No plan or approval is needed; the maintainer assigns it to you.
- **Issues are labelled by difficulty.** `good first issue` is small and self-contained; `help wanted` needs more
  context and sometimes a short design note in the issue.
- **Keep it moving.** If there is no pull request or update for two weeks, the issue goes back to the pool so that
  someone else can pick it up. Need more time? Just say so in a comment.
- **Stay in scope.** What an issue lists under "Out of scope" is left out of the pull request; it is usually an
  issue of its own.

## Setting up

Prerequisites:

- **JDK 21** - the build targets Java 21 and fails on older JDKs.
- **Docker** - integration and end-to-end tests start PostgreSQL and Kafka with Testcontainers. Without Docker the
  build still passes, only those tests are skipped.
- Maven is not required, use the bundled wrapper `./mvnw`.

Fork the repository on GitHub, then:

```shell
git clone git@github.com:<your-account>/spring-transactional-outbox.git
cd spring-transactional-outbox
git remote add upstream git@github.com:dmitriy-iliyov/spring-transactional-outbox.git
git checkout -b fix/<short-description>
```

**IDE.** Open the root `pom.xml` as a Maven project. The services use Lombok, so the IDE has to process its
annotations: in IntelliJ IDEA the Lombok plugin is bundled, and *Settings → Build, Execution, Deployment →
Compiler → Annotation Processors → Enable annotation processing* must be on. Without it every constructor
injection shows up as an error although the Maven build is green.

**Keeping your fork up to date:**

```shell
git fetch upstream
git rebase upstream/main
git push --force-with-lease
```

## Project structure

| Module | Purpose |
|---|---|
| `events-contract` | The events shared by both services: the sealed `OrderEvent` and its records, the event type names |
| `order-service` | REST API for orders; publishes `order-created` and `order-cancelled` through the oncebox outbox to Kafka, with a dead letter queue and its REST API |
| `tests/order-service-e2e-tests` | End-to-end tests of the packaged order service over PostgreSQL and Kafka |
| `notification-service` | Consumes order events through the oncebox transactional inbox, exactly one notification per event; failed records go to a dead letter topic |
| `tests/notification-service-e2e-tests` | End-to-end tests of the packaged notification service, with the order service played over Kafka |

Base package: `io.github.outbox` (`.contract`, `.order`, `.notification`). The contract module holds only
events; request and response records live in the service that owns the API.

The outbox and the inbox come from the [oncebox](https://github.com/dmitriy-iliyov/oncebox) starter. A defect in
publishing, retries, the dead letter queue, stuck events or deduplication themselves belongs in the oncebox
repository; an issue here can link to it.

## Building and testing

Build everything and run every test level, the coverage check included:

```shell
./mvnw verify
```

One module, or one test class:

```shell
./mvnw -pl order-service verify
./mvnw -pl notification-service test -Dtest=NotificationServiceUnitTest
```

An end-to-end module runs against the packaged service, so build it together with what it depends on:

```shell
./mvnw -pl tests/order-service-e2e-tests -am verify
```

Each module fails the build below **80% line coverage** (JaCoCo). Tests that need Docker are skipped on a machine
without it, but CI runs all of them, so a change touching the database or Kafka is worth checking with Docker
running.

To run a service by hand, point it at a PostgreSQL database and a Kafka broker through the environment:
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `SERVER_PORT`. The order
service listens on 8080 and the notification service on 8081 by default; tables are created on startup by
Liquibase and by oncebox.

## Troubleshooting

- **Integration tests are skipped although Docker is installed** - Docker is not running, or Testcontainers
  cannot find it. Check `docker info`. With Colima or Rancher Desktop, point Testcontainers at the socket:
  `export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
  `export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
- **The first build is slow** - Maven downloads its dependencies and Docker pulls the PostgreSQL and Kafka images
  once; later builds reuse them.
- **The IDE shows errors that Maven does not** - annotation processing is off, see [Setting up](#setting-up).
- **`Port 8080 was already in use`** when running a service - another process holds the port; start the service
  with `SERVER_PORT=<free port>`.
- **The build fails on coverage** - the message names the module. Your change added code without tests for it;
  add them at the level the code belongs to (see the table below).

## Conventions for every change

Code, comments, javadoc and messages are written in English. Keep a change focused on one thing; unrelated
refactoring belongs in a pull request of its own.

**Formatting.** Four-space indent, lines up to about 120 characters, explicit local types (no `var`),
`"...%s".formatted(...)` rather than `String.format`.

**Tests.** A bug fix comes with a test that fails without the fix; new behaviour comes with tests for it.

| Level | Suffix | `@DisplayName` prefix | What runs |
|---|---|---|---|
| Unit | `*UnitTest` | `UT` | one class, its collaborators mocked with Mockito |
| Integration | `*IntegrationTest` | `IT` | a Spring slice (`@WebMvcTest`, `ApplicationContextRunner`) or a real PostgreSQL / Kafka in Testcontainers |
| End-to-end | `*EndToEndTest` | `E2E` | the packaged service started as in production, driven over HTTP and Kafka |

- Methods are named `method_whenCondition_shouldResult`, and the display name spells it out:
  `@DisplayName("UT create() when several items are ordered should store the sum of their subtotals as total")`.
- The body is split by `// given`, `// when`, `// then` comments; the object under test is called `tested`.
- Assertions are AssertJ. Helpers and test data builders go to the bottom of the class, or to a class of their own
  whose name does not end in `Test`. Tests do not use Lombok.
- No `Thread.sleep` and no dependency on the wall clock: time comes from a `Clock`, asynchronous effects are
  awaited through the module's `Waits` helper. End-to-end tests use fresh identifiers in every case, since their
  state is not cleaned between tests.

**Javadoc and comments.** A comment says what the signature cannot: the contract, what `null` means, the
invariant behind a value, or the reason for a decision that would otherwise look like a mistake. It does not
restate the name of a method. Public types get javadoc; getters, one-liners and trivial private helpers get none.

## Conventions for the services' core

Read this part when your change touches the API, the error responses, the configuration, the database or the
outbox and inbox wiring.

- **Lombok** is used for constructors and loggers only (`@RequiredArgsConstructor`, `@Slf4j`). `lombok.config`
  copies `@Qualifier`, `@TransactionalConsumer` and `@NonTransactionalConsumer` from fields onto the generated
  constructor; a new qualifier annotation on an injected field has to be added there too, or the wrong bean is
  injected without any error.
- **Controllers stay thin**: they bind and validate the request and hand it over. Request bodies are records with
  Bean Validation constraints, each with its own message.
- **Errors** reach the client as RFC 9457 problem details, written by the service's `@RestControllerAdvice`. A new
  error gets its own `type` in `ProblemTypes` under `https://spring-transactional-outbox/errors/`; a validation
  failure lists the offending fields in `errors`; a 500 never carries the cause.
- **Configuration** of the application lives under `app.*` in a validated `@ConfigurationProperties` record, so a
  wrong value stops the startup naming the property. Settings of the outbox and the inbox stay under `oncebox.*`.
- **Timeouts.** The Kafka producer timeouts of the order service are tied to the outbox thresholds
  (`emergency-timeout`, stuck recovery): a send has to give up before oncebox considers the event lost. Changing one
  side means checking the other; `OutboxTimeoutsUnitTest` holds the relations.
- **Schema.** A change is a new Liquibase changeset; a changeset that was released is never edited. The oncebox
  tables are created by the library itself.
- **Tests at this level** never mock a component of the module under test in an integration or end-to-end test,
  only what lies outside it; containers shared by a cached Spring context are singletons started once per JVM.

## Pull requests and review

1. Push your branch to your fork and open a pull request against `main`.
2. Title it like a commit: `type(lowercase summary)`, for example `fix(order totals above the column range answer
   400)` or `test(unit tests for kafka consumer properties)`. Types: `feat`, `fix`, `test`, `docs`, `chore`, `ci`.
3. In the description, say what changed and why, and link the issue (`Fixes #123`).

Pull requests are **squash-merged**: the title becomes the commit message, so the commits inside your branch can
be named however you like.

A pull request is merged when:

- the build is green and coverage stays above the threshold;
- new code has tests at the level it belongs to;
- it changes one thing, and what is out of scope of its issue is left out;
- review comments are resolved.

Expect a first review within a few days. Review comments are suggestions to discuss, not orders: if you disagree,
say why. Do not edit `CHANGELOG.md` - the maintainer writes it at release time from the merged pull requests.

## Reporting bugs and proposing changes

**A bug report** includes:

- what you did - the request, or the event with its `oncebox_*` headers;
- what you expected and what happened instead;
- the logs or the stack trace;
- the versions of Java and Docker, and the commit or release you ran.

**A feature or a larger change** starts as an issue describing the use case. Agreeing on the approach before
writing the code saves both sides a rewrite.

## Security issues

Do not report a vulnerability in a public issue. Use **Report a vulnerability** on the repository's Security tab,
which opens a private advisory visible only to the maintainer.

By contributing you agree that your contribution is licensed under the [Apache License 2.0](LICENSE) and that you
follow the [Code of Conduct](CODE_OF_CONDUCT.md).
