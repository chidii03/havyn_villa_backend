PRODUCT REQUIREMENTS DOCUMENT (PRD)
HavynVilla — Cloud-Native DevOps Engineering Platform
Project type: Final-semester Software Engineering / DevOps projectExisting application: HavynVilla — https://havynvilla.vercel.app Application domain: Property listing and short-let booking marketplacePrimary development environment: GitHub CodespacesImplementation approach: Audit and extend the existing application; do not rebuild it from scratchFrontend: Next.jsBackend: Java Spring BootPrimary database: PostgreSQLCache / supporting service: RedisPayment integration: PaystackContainerization: DockerOrchestration: KubernetesCI/CD: JenkinsMonitoring: Prometheus and GrafanaRepository: Use the dedicated DevOps project repository, separate from production



1. Project Overview
Transform the existing HavynVilla property marketplace into a well-documented, containerized, observable, and continuously delivered application that demonstrates modern DevOps engineering practices.
HavynVilla allows users to discover property listings and book short-let accommodation. The existing system has a Next.js frontend, Spring Boot backend, PostgreSQL database, Redis integration, Paystack payment integration, and existing Docker/Kubernetes configuration files.
The objective is to improve the application’s deployment automation, reproducibility, reliability, monitoring, security, testing, and operational readiness while preserving its existing business functionality.
This is an extension of an existing application, not a greenfield development project.
2. Critical Instructions: Audit Before Making Changes
Before implementing anything, inspect the entire repository and produce a concise technical audit.
Identify:
* The frontend directory, framework version, package manager, build scripts, and required environment variables.
* The Spring Boot backend directory, Java version, Maven or Gradle build system, profiles, dependencies, and API configuration.
* The PostgreSQL integration, ORM, schema, migration tool, and database configuration.
* The Redis integration and its actual use cases.
* How Paystack initialization, callback/verification, and webhook processing currently work.
* Existing Dockerfiles, .dockerignore files, Docker Compose files, Kubernetes manifests, Jenkinsfiles, and monitoring configuration.
* Existing tests, health endpoints, logging, metrics, and error handling.
* Existing hosting configuration and any platform-specific dependencies.
* Current security risks, hardcoded values, duplicated configurations, and incomplete infrastructure files.
Create docs/architecture-audit.md containing the current architecture, discovered issues, missing components, proposed improvements, and implementation sequence.
Do not begin by replacing existing configuration files. Determine which components already work, which are incomplete, and which can be reused.
Do not assume that a file is functional simply because it exists.
After the audit, proceed in small, verifiable phases. Preserve the existing business logic and API contracts unless a change is genuinely required and documented.
3. Project Objectives
The completed project must demonstrate:
1. Reproducible container builds for the frontend and backend.
2. Reliable local development using Docker Compose.
3. Automated source validation, tests, builds, and image creation.
4. Jenkins-based CI/CD with a documented deployment workflow.
5. Kubernetes deployment of the application components.
6. Secure configuration and secret management.
7. PostgreSQL and Redis connectivity, readiness, and failure handling.
8. Application and infrastructure monitoring using Prometheus.
9. Operational dashboards using Grafana.
10. Structured logging and useful troubleshooting documentation.
11. Automated smoke tests and deployment verification.
12. Demonstrable recovery and failure scenarios in a test environment.
13. A complete final-semester project report and evidence of testing.
4. Target Architecture
Use the following as the intended architecture, adapting it to the actual repository and environment:
Developer → GitHub → Jenkins CI/CD → Build and test → Build container images → Publish images if a registry is available → Deploy to Kubernetes → Smoke tests and verification.
The deployed application consists of:
* Next.js frontend: User-facing marketplace interface.
* Spring Boot backend: API and business logic.
* PostgreSQL: Persistent application data.
* Redis: Existing caching, session, or other confirmed application use cases.
* Paystack: External payment service, using test mode for development and demonstrations.
* Prometheus: Metrics collection.
* Grafana: Dashboards and visualization.
* Jenkins: Automated validation, builds, and controlled deployments.
* Kubernetes: Workload orchestration, service discovery, configuration, and recovery.
Use Kubernetes Services for internal communication. Expose only the necessary application entry point through an Ingress or another supported method.
Do not introduce additional infrastructure products unless they materially improve the project and can be operated within the available budget.
5. Docker Requirements
Audit and improve the existing Docker configuration rather than blindly creating replacements.
Frontend
* Build the existing Next.js application using its current package manager and supported production build process.
* Use a multi-stage Dockerfile where appropriate.
* Run the production application using the repository’s actual deployment configuration.
* Avoid copying development dependencies and unnecessary files into the final image.
* Use a non-root runtime user where feasible.
* Add appropriate .dockerignore rules.
* Ensure build-time and runtime environment variables are handled correctly.
* Never bake secrets into the image.
Backend
* Use the Java version required by the project.
* Support the existing Maven or Gradle build system.
* Use a multi-stage Docker build where practical.
* Run the Spring Boot application with a non-root user where feasible.
* Configure JVM memory and container resource usage sensibly.
* Keep database credentials, Redis credentials, Paystack secrets, and other sensitive settings outside the image.
Image quality
* Use explicit, supported base image versions rather than relying on latest.
* Ensure the images can be built repeatedly.
* Add image scanning where it can be run with available tooling.
* Document build commands and the purpose of each stage.
* Do not claim an image is secure simply because it builds successfully.
6. Docker Compose Development Environment
Create or improve a root-level Docker Compose configuration suitable for local development and GitHub Codespaces.
It should support, as appropriate:
* Next.js frontend.
* Spring Boot backend.
* PostgreSQL.
* Redis.
Requirements:
* Configure service-to-service networking.
* Use health checks for services where supported.
* Configure startup dependencies and application-level retry behavior appropriately.
* Use named volumes for local PostgreSQL persistence.
* Configure Redis persistence only if consistent with the application’s requirements.
* Use an isolated development database and development Redis configuration.
* Provide a .env.example containing placeholder values only.
* Ensure local configuration is not confused with production configuration.
* Document startup, shutdown, logs, health checks, and database reset procedures.
Paystack must use test credentials and test transactions in the development environment.
Do not require production credentials to run the development stack.
7. PostgreSQL Requirements
PostgreSQL is the primary persistent database.
First, identify the application’s actual data-access implementation, ORM, schema, and migration approach. Preserve the existing technology where possible.
Requirements:
* Configure database connection settings through environment variables.
* Keep credentials out of source control.
* Use a dedicated development/test database.
* Configure Kubernetes database connectivity using the appropriate service or managed database endpoint.
* Implement or verify readiness checks that distinguish application startup from database readiness.
* Verify migrations are repeatable and appropriately ordered.
* Document development database initialization and backup/recovery procedures.
* Test the application’s behavior when PostgreSQL is temporarily unavailable.
* Verify that the application can recover after connectivity returns.
* Never execute destructive database tests against production.
* Do not automatically assume that production data should be copied into the development environment.
If PostgreSQL runs inside Kubernetes for the demonstration, use persistent storage and clearly document its limitations. If a managed database is more appropriate, keep it external and configure secure connectivity.
Do not claim production-grade database high availability or disaster recovery unless it has actually been implemented and tested.
8. Redis Requirements
Preserve Redis because it is already part of the application.
Audit how the backend uses Redis before changing its configuration.
Requirements:
* Identify whether Redis supports caching, sessions, rate limiting, queues, or other functionality.
* Configure the connection through environment variables.
* Provide an isolated development/test instance or test endpoint.
* Add a Redis health check where appropriate.
* Ensure that Redis being temporarily unavailable does not cause uncontrolled application crashes.
* Implement suitable timeouts and failure behavior for the confirmed Redis use cases.
* Test cache misses, Redis unavailability, and recovery where relevant.
* Do not use Redis as the source of truth for persistent booking or payment records unless the existing architecture explicitly requires it.
* Do not expose Redis publicly without a justified need and suitable protection.
* Document whether Redis persistence is required for the actual use case.
Do not invent caching or session behavior that the application does not currently implement.
9. Paystack Payment Safety and Testing
Paystack is an existing external payment integration and must remain functional.
Requirements:
* Preserve the current payment workflow and API contracts.
* Inspect how payment initialization, payment verification, callbacks, and webhooks work.
* Use Paystack test mode and test credentials for all routine CI/CD and DevOps demonstrations.
* Keep secret keys and webhook verification secrets out of source control, images, browser bundles, logs, and metrics.
* Never send live payment requests from automated tests.
* Ensure that booking or payment status is not considered successful solely because the frontend redirects to a success page.
* Verify the existing backend-side payment verification and webhook validation behavior.
* Check whether webhook processing is safe against duplicate delivery and repeated requests.
* Do not change payment business logic unless a verified issue requires it.
* Document the separation between test and production payment environments.
Never use real customer transactions or production payment secrets for failure testing.
10. Jenkins CI/CD Pipeline
Create or improve a Jenkinsfile that reflects the actual repository structure.
The pipeline should support these stages:
1. Checkout source code.
2. Validate required tools and configuration.
3. Install frontend dependencies using the project’s lockfile and package manager.
4. Run frontend linting, type checking, and tests where configured.
5. Build the frontend.
6. Compile the Spring Boot backend.
7. Run backend unit and integration tests.
8. Build container images.
9. Perform image or dependency security checks where tooling is available.
10. Publish images to a configured container registry when credentials and access are available.
11. Validate Kubernetes manifests.
12. Deploy only to the designated test environment when deployment credentials and cluster access are configured.
13. Run deployment smoke tests.
14. Report the result and retain useful logs and test reports.
Pipeline rules:
* Fail the pipeline when mandatory tests or builds fail.
* Do not embed registry credentials or deployment tokens in the Jenkinsfile.
* Use Jenkins credentials or the appropriate secret store.
* Do not deploy automatically to the live HavynVilla production environment.
* Use an explicit approval step before any separately authorized production deployment.
* Tag images with immutable identifiers such as the Git commit SHA.
* Avoid relying solely on the mutable latest image tag.
* Keep test, staging, and production settings separate.
* Document Jenkins installation or access requirements and the exact steps for running the pipeline.
If Jenkins or a Kubernetes cluster cannot be operated in the available Codespaces environment, implement and validate the pipeline configuration as far as possible, clearly documenting the missing runtime requirement. Never report an unexecuted deployment as successful.
11. Kubernetes Requirements
Audit and improve the existing Kubernetes manifests.
Provide organized manifests for the frontend, backend, internal services, configuration, and monitoring components where applicable.
Use a clear directory structure, such as:
* k8s/base/
* k8s/overlays/dev/
* k8s/overlays/staging/
Adapt this structure if the existing repository already uses a sensible alternative.
Application workloads
* Create separate Deployments for the frontend and backend.
* Configure appropriate Services for internal communication.
* Configure the application entry point through Ingress or another supported approach.
* Define CPU and memory requests and limits.
* Configure readiness and liveness probes appropriately.
* Use rolling updates where supported.
* Configure replica counts based on actual resource availability.
* Use immutable image tags.
* Use ConfigMaps for non-sensitive configuration.
* Use Kubernetes Secrets or an appropriate external secret manager for sensitive configuration.
* Avoid committing real Secret values.
* Configure graceful shutdown where supported.
* Verify that the frontend can reach the backend using the correct internal or external URL.
PostgreSQL and Redis
* Determine whether these dependencies should be managed inside Kubernetes or remain external.
* If PostgreSQL is deployed inside the cluster for the demonstration, provide persistent storage configuration and document backup and recovery limitations.
* Do not assume a default local cluster provides durable production-grade storage.
* Redis deployment must match the confirmed application requirements.
* Keep credentials and sensitive connection strings out of committed manifests.
* Never point the DevOps namespace at production dependencies during testing.
Operational controls
* Include resource quotas or namespace-level resource controls if supported by the target cluster.
* Add PodDisruptionBudgets only where their behavior and replica requirements make sense.
* Document rollout status, rollback, logs, and troubleshooting commands.
* Test configuration changes and controlled recovery in the test environment.
Use the simplest Kubernetes setup that demonstrates the requirements reliably. Do not introduce a complex cluster architecture solely for appearance.
12. Prometheus Monitoring
Implement Prometheus monitoring for the application and available infrastructure.
Spring Boot backend
Inspect the existing Spring Boot version and dependency configuration.
If compatible, configure Spring Boot Actuator and Micrometer Prometheus metrics using the appropriate dependencies.
Requirements:
* Expose the health endpoints needed for orchestration.
* Expose application metrics through an appropriately secured internal endpoint.
* Configure Prometheus scraping using the discovered endpoint.
* Verify that metrics are actually returned and successfully scraped.
* Use existing metrics where suitable.
* Add custom business metrics only when they provide useful operational information.
Potential metrics, if supported by the application, include:
* HTTP request counts.
* HTTP request latency.
* HTTP error rates.
* JVM memory and garbage collection.
* Active and pending requests where available.
* Database connection pool health and utilization.
* Redis connectivity and operation behavior.
* Booking creation outcomes.
* Payment verification outcomes, without sensitive payment data.
Frontend
Add useful frontend monitoring where feasible.
Do not assume Prometheus can scrape browser-side metrics automatically. If frontend metrics are needed, implement a suitable server-side or application-level instrumentation approach.
Infrastructure
* Configure Prometheus scraping for supported application and infrastructure targets.
* Add infrastructure exporters only when the corresponding component is deployed and the exporter is appropriate.
* Avoid exposing metrics endpoints publicly.
* Do not use labels containing user IDs, email addresses, booking IDs, payment references, tokens, or other high-cardinality or sensitive data.
* Do not claim that a service is monitored unless Prometheus is configured to collect its metrics.
13. Grafana Dashboards
Create Grafana dashboards using actual Prometheus metrics.
Include useful panels such as:
* Frontend/backend availability.
* Request rate.
* HTTP error rate.
* Response latency.
* JVM memory and CPU behavior.
* Backend pod health and restarts.
* Database connection pool metrics where available.
* Redis health and performance where available.
* Deployment or service health indicators.
Include sensible thresholds and explain what each panel helps an operator identify.
Create alert rules for relevant conditions where supported, such as:
* Backend unavailable.
* Elevated error rates.
* Sustained high response latency.
* Excessive pod restarts.
* Resource exhaustion.
* Failed readiness checks.
Avoid arbitrary thresholds without explaining their purpose. Test alerts where the environment permits and document any limitations.
Provision dashboards and data sources through configuration where practical so the setup is reproducible.
14. Logging and Troubleshooting
Implement or improve structured application logging.
Requirements:
* Include timestamps, severity, service name, and useful request context.
* Avoid logging passwords, access tokens, Paystack secrets, full payment credentials, or sensitive personal data.
* Avoid logging entire payment payloads or unnecessary customer information.
* Make frontend and backend logs distinguishable.
* Document how to inspect container logs and Kubernetes pod logs.
* Include common troubleshooting steps for failed builds, unavailable dependencies, failed deployments, and unhealthy pods.
* Preserve enough context to investigate failures without exposing sensitive information.
If centralized logging is not implemented, document that limitation rather than claiming centralized log aggregation exists.
15. Automated Testing and Reliability
Use the application’s existing test framework wherever possible.
Implement or improve tests covering:
* Frontend build and important existing UI behavior.
* Backend unit tests.
* Backend API integration tests.
* Database connectivity and repository operations.
* Redis integration where relevant.
* Booking workflow validation.
* Payment verification logic using mocks or Paystack test mode.
* Health and readiness endpoints.
* Deployment smoke tests.
Add controlled failure scenarios in a dedicated test environment:
1. Restart an application pod and verify recovery.
2. Simulate backend unavailability and verify the frontend or ingress behaves appropriately.
3. Test backend behavior when PostgreSQL is unavailable.
4. Test Redis failure behavior according to the actual integration.
5. Introduce a failing test and verify that Jenkins blocks the pipeline.
6. Deploy a known-good version after a failed deployment and document the recovery process.
7. Verify that secrets are not printed during builds or application execution.
Use mocks or isolated dependencies for automated tests wherever practical.
Do not perform denial-of-service tests, destructive database tests, live payment tests, or production failure simulations.
16. GitHub Codespaces Constraints
The project must be developed primarily through GitHub Codespaces because the developer does not rely on a local Docker installation on Windows.
Requirements:
* Inspect the available Codespaces CPU, memory, storage, and permissions.
* Determine whether Docker builds and Kubernetes tooling can run in the selected Codespaces environment.
* Prefer a lightweight, reproducible development workflow.
* Avoid requiring a paid cloud cluster or paid observability service for the basic demonstration.
* Document which components can run in Codespaces and which require an external environment.
* Provide alternative validation steps when privileged container operations or nested virtualization are unavailable.
* Do not assume that Docker, Jenkins, and a full Kubernetes cluster can all run reliably within one Codespace.
* If a remote cluster is used, keep its credentials in the appropriate secure secret store.
* Avoid committing kubeconfig files, access tokens, private keys, or real credentials.
The project should remain useful even if the full Jenkins/Kubernetes runtime cannot be operated simultaneously in the Codespace.
17. Security Requirements
Apply basic security practices throughout the project.
* Keep all real credentials out of Git.
* Provide .env.example files with placeholders only.
* Audit tracked files for accidentally committed secrets.
* Keep production and development secrets separate.
* Do not reuse production database credentials in CI.
* Do not expose PostgreSQL, Redis, Jenkins, or Prometheus publicly without a justified and protected configuration.
* Use least-privilege credentials where supported.
* Run containers as non-root users where feasible.
* Avoid unnecessary container privileges.
* Keep dependencies reasonably current and check for known vulnerabilities.
* Validate external input through the existing backend validation mechanisms.
* Preserve authentication and authorization behavior.
* Document any unresolved security findings.
Never include secret values in the final report, test logs, sample manifests, or screenshots.
18. Documentation Deliverables
Create or update the following documentation based on the actual implementation:
* README.md — project overview and quick start.
* docs/architecture-audit.md — original application audit.
* docs/architecture.md — architecture diagram and component descriptions.
* docs/local-development.md — Codespaces and Docker Compose setup.
* docs/docker.md — image builds and troubleshooting.
* docs/database.md — PostgreSQL setup, migrations, and recovery.
* docs/redis.md — Redis role and configuration.
* docs/payments.md — safe Paystack test-mode workflow.
* docs/jenkins-cicd.md — pipeline setup and execution.
* docs/kubernetes.md — deployment, verification, and rollback.
* docs/monitoring.md — Prometheus and Grafana setup.
* docs/testing.md — automated tests and failure simulations.
* docs/security.md — secrets and security practices.
* docs/final-report.md — academic project report.
The final report should cover:
1. Introduction and problem statement.
2. Aim and objectives.
3. Existing system and initial audit.
4. Proposed DevOps architecture.
5. Technology selection and justification.
6. Implementation details.
7. CI/CD workflow.
8. Containerization and Kubernetes deployment.
9. Monitoring and observability.
10. Testing and results.
11. Failure recovery demonstrations.
12. Security considerations.
13. Challenges and limitations.
14. Conclusion and future improvements.
Include architecture diagrams, pipeline diagrams, test evidence, and screenshots only when they correspond to work actually performed.
19. Definition of Done
The project is complete when:
* The existing application has been audited and its functionality preserved.
* The frontend and backend build successfully.
* Docker images can be built reproducibly.
* The local development stack is documented and works in the supported environment.
* PostgreSQL and Redis integrations are configured appropriately for development.
* Automated tests run and report meaningful results.
* Jenkins pipeline stages are implemented and validated to the extent supported by the environment.
* Kubernetes manifests are validated and the application deploys successfully if a suitable cluster is available.
* Application health checks work correctly.
* Prometheus collects verified metrics.
* Grafana dashboards display real collected data.
* Failure and recovery scenarios are documented with evidence.
* Secrets and production services remain protected.
* All documentation accurately distinguishes implemented features from planned or unverified features.
If an environment limitation prevents a live demonstration, document the exact limitation and provide the strongest available validation evidence. Do not falsely mark unexecuted stages as successful.
20. Implementation Instructions for Codex
Work in phases.
Phase 1 — Audit only
Inspect the repository, identify its structure and dependencies, review existing infrastructure files, and produce docs/architecture-audit.md. Report any blocking issues. Do not rewrite files during this phase.
Phase 2 — Development environment
Improve the Dockerfiles, Docker Compose configuration, development environment examples, and local documentation. Validate builds and connectivity where possible.
Phase 3 — Automated testing and CI
Implement or improve automated tests and the Jenkins pipeline. Verify that failures stop the pipeline as expected.
Phase 4 — Kubernetes
Audit and improve manifests, configure workloads and health probes, validate resources, and deploy to an available test cluster if possible.
Phase 5 — Observability
Implement supported metrics, configure Prometheus scraping, provision Grafana dashboards, and verify that data is actually collected.
Phase 6 — Reliability and security
Run controlled failure tests, verify recovery behavior, check secret handling, and resolve identified issues.
Phase 7 — Documentation and final validation
Complete the report, record actual test results, list environment limitations, and provide reproducible instructions for the final demonstration.
At the end of every phase, summarize:
* Files created or changed.
* What was implemented.
* Commands and tests executed.
* Actual test results.
* Outstanding issues.
* The next recommended step.
Final non-negotiable instructions
* Do not rewrite HavynVilla from scratch.
* Do not remove working business functionality to simplify the DevOps implementation.
* Do not replace Spring Boot with Node.js or change the existing primary database without a verified technical reason.
* Do not assume existing Docker/Kubernetes files are correct; inspect and test them.
* Do not deploy to or modify the live production environment without explicit authorization.
* Do not use production payment credentials or production data for development testing.
* Do not invent test results, deployment outcomes, metrics, screenshots, or evidence.
* Prefer simple, reliable, reproducible engineering over unnecessary complexity.
* Begin with Phase 1: Audit only and wait for the audit to be reviewed before making broad changes.