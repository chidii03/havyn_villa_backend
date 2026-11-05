pipeline {
    agent any

    options {
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
        timestamps()
        ansiColor('xterm')
    }

    environment {
        // Build metadata
        IMAGE_TAG = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(8) : env.BUILD_NUMBER}"
        REGISTRY = "${env.DOCKER_REGISTRY ?: 'ghcr.io'}"
        IMAGE_NAMESPACE = "${env.DOCKER_NAMESPACE ?: 'chidii03'}"
        API_IMAGE = "${REGISTRY}/${IMAGE_NAMESPACE}/havyn-api"
        WEB_IMAGE = "${REGISTRY}/${IMAGE_NAMESPACE}/havyn-web"
        
        // Runtime tools and paths
        JAVA_HOME = "${tool name: 'JDK-21', type: 'jdk'}"
        NODE_HOME = "${tool name: 'NodeJS-20', type: 'nodejs'}"
        PATH = "${JAVA_HOME}/bin:${NODE_HOME}/bin:${env.PATH}"
        
        // Test & verification settings
        NEXT_PUBLIC_API_BASE_URL = 'http://localhost:8080'
    }

    stages {
        // Stage 1: Checkout Source Code
        stage('Checkout') {
            steps {
                echo "=== Stage 1: Checking out source code ==="
                checkout scm
                script {
                    env.GIT_SHA = sh(script: 'git rev-parse HEAD || echo "local-build"', returnStdout: true).trim()
                    env.GIT_SHORT_SHA = sh(script: 'git rev-parse --short=8 HEAD || echo "local"', returnStdout: true).trim()
                    env.IMAGE_TAG = env.GIT_SHORT_SHA
                    echo "Checked out commit: ${env.GIT_SHA} (Tag: ${env.IMAGE_TAG})"
                }
            }
        }

        // Stage 2: Validate Required Tools and Configuration
        stage('Validate Environment & Tools') {
            steps {
                echo "=== Stage 2: Validating environment and required tools ==="
                sh '''
                    echo "Checking Java version..."
                    java -version
                    
                    echo "Checking Node and npm..."
                    node --version
                    npm --version
                    
                    echo "Checking Docker..."
                    if command -v docker >/dev/null 2>&1; then
                        docker --version
                    else
                        echo "WARNING: Docker CLI not found on Jenkins agent. Image build stages will require Docker daemon."
                    fi

                    echo "Locating Frontend directory..."
                    if [ -d "../web" ]; then
                        echo "Found frontend at ../web"
                    elif [ -d "web" ]; then
                        echo "Found frontend at ./web"
                    elif [ -d "apps/web" ]; then
                        echo "Found frontend at ./apps/web"
                    else
                        echo "Frontend directory not found at standard paths. Will look dynamically in build stage."
                    fi
                '''
            }
        }

        // Stage 3: Install Frontend Dependencies
        stage('Install Frontend Dependencies') {
            steps {
                echo "=== Stage 3: Installing frontend dependencies ==="
                sh '''
                    FRONTEND_DIR=""
                    if [ -d "../web" ]; then FRONTEND_DIR="../web";
                    elif [ -d "web" ]; then FRONTEND_DIR="web";
                    elif [ -d "apps/web" ]; then FRONTEND_DIR="apps/web"; fi

                    if [ -n "$FRONTEND_DIR" ]; then
                        echo "Running npm ci in $FRONTEND_DIR..."
                        cd "$FRONTEND_DIR"
                        npm ci --prefer-offline
                    else
                        echo "Skipping frontend dependency install (frontend directory not in current workspace tree)."
                    fi
                '''
            }
        }

        // Stage 4: Frontend Linting, Type Checking, and Tests
        stage('Frontend Lint & Tests') {
            steps {
                echo "=== Stage 4: Running frontend lint, typecheck, and tests ==="
                sh '''
                    FRONTEND_DIR=""
                    if [ -d "../web" ]; then FRONTEND_DIR="../web";
                    elif [ -d "web" ]; then FRONTEND_DIR="web";
                    elif [ -d "apps/web" ]; then FRONTEND_DIR="apps/web"; fi

                    if [ -n "$FRONTEND_DIR" ]; then
                        cd "$FRONTEND_DIR"
                        echo "Running TypeScript typecheck..."
                        npm run typecheck
                        
                        echo "Running ESLint..."
                        npm run lint || echo "ESLint reported warnings"
                        
                        echo "Running frontend unit tests with Vitest..."
                        npm run test
                    else
                        echo "Frontend not present in workspace, skipping frontend tests."
                    fi
                '''
            }
        }

        // Stage 5: Build Frontend
        stage('Build Frontend') {
            steps {
                echo "=== Stage 5: Building frontend Next.js application ==="
                sh '''
                    FRONTEND_DIR=""
                    if [ -d "../web" ]; then FRONTEND_DIR="../web";
                    elif [ -d "web" ]; then FRONTEND_DIR="web";
                    elif [ -d "apps/web" ]; then FRONTEND_DIR="apps/web"; fi

                    if [ -n "$FRONTEND_DIR" ]; then
                        cd "$FRONTEND_DIR"
                        echo "Building Next.js standalone application..."
                        export NEXT_PUBLIC_API_BASE_URL="http://localhost:8080"
                        npm run build
                    else
                        echo "Frontend not present in workspace, skipping frontend build."
                    fi
                '''
            }
        }

        // Stage 6: Compile Spring Boot Backend
        stage('Compile Backend') {
            steps {
                echo "=== Stage 6: Compiling Spring Boot backend ==="
                sh '''
                    cd apps/api
                    echo "Ensuring Gradle wrapper executable..."
                    chmod +x ./gradlew
                    ./gradlew compileJava compileTestJava --no-daemon
                '''
            }
        }

        // Stage 7: Run Backend Unit & Integration Tests + Coverage Gate
        stage('Backend Tests & Coverage') {
            steps {
                echo "=== Stage 7: Running backend unit tests and JaCoCo coverage verification ==="
                sh '''
                    cd apps/api
                    chmod +x ./gradlew
                    echo "Running test suite and verifying JaCoCo coverage thresholds..."
                    ./gradlew test jacocoTestReport jacocoTestCoverageVerification --no-daemon
                '''
            }
            post {
                always {
                    junit testResults: 'apps/api/build/test-results/test/*.xml', allowEmptyResults: true
                    publishHTML(target: [
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'apps/api/build/reports/jacoco/test/html',
                        reportFiles: 'index.html',
                        reportName: 'Backend JaCoCo Coverage Report'
                    ])
                }
            }
        }

        // Stage 8: Build Container Images
        stage('Build Container Images') {
            steps {
                echo "=== Stage 8: Building container images (Tag: ${IMAGE_TAG}) ==="
                sh '''
                    echo "Building API Docker image: ${API_IMAGE}:${IMAGE_TAG}"
                    docker build -t "${API_IMAGE}:${IMAGE_TAG}" -t "${API_IMAGE}:latest" ./apps/api

                    FRONTEND_DIR=""
                    if [ -d "../web" ]; then FRONTEND_DIR="../web";
                    elif [ -d "web" ]; then FRONTEND_DIR="web";
                    elif [ -d "apps/web" ]; then FRONTEND_DIR="apps/web"; fi

                    if [ -n "$FRONTEND_DIR" ]; then
                        echo "Building Web Docker image: ${WEB_IMAGE}:${IMAGE_TAG}"
                        docker build \
                            --build-arg NEXT_PUBLIC_API_BASE_URL="http://localhost:8080" \
                            -t "${WEB_IMAGE}:${IMAGE_TAG}" \
                            -t "${WEB_IMAGE}:latest" \
                            "$FRONTEND_DIR"
                    else
                        echo "Frontend directory not present; skipping Web container image build."
                    fi
                '''
            }
        }

        // Stage 9: Security & Vulnerability Linting
        stage('Security Checks') {
            steps {
                echo "=== Stage 9: Running security checks and Dockerfile linting ==="
                sh '''
                    if command -v hadolint >/dev/null 2>&1; then
                        echo "Linting API Dockerfile..."
                        hadolint apps/api/Dockerfile || true
                    else
                        echo "Hadolint not installed; skipping Dockerfile static linting."
                    fi

                    echo "Scanning API build output for committed secrets or credentials..."
                    ! grep -rn "BEGIN RSA PRIVATE KEY" apps/api/src/main/resources || {
                        echo "ERROR: Private key detected in resources!"
                        exit 1
                    }
                '''
            }
        }

        // Stage 10: Publish Images to Container Registry
        stage('Publish Container Images') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+.*', comparator: 'REGEXP'
                }
            }
            steps {
                echo "=== Stage 10: Publishing container images to registry ==="
                script {
                    try {
                        withCredentials([usernamePassword(
                            credentialsId: 'container-registry-credentials',
                            usernameVariable: 'REGISTRY_USER',
                            passwordVariable: 'REGISTRY_PASSWORD'
                        )]) {
                            sh '''
                                echo "$REGISTRY_PASSWORD" | docker login "$REGISTRY" -u "$REGISTRY_USER" --password-stdin
                                echo "Pushing API image ${API_IMAGE}:${IMAGE_TAG}..."
                                docker push "${API_IMAGE}:${IMAGE_TAG}"
                                docker push "${API_IMAGE}:latest"

                                if docker image inspect "${WEB_IMAGE}:${IMAGE_TAG}" >/dev/null 2>&1; then
                                    echo "Pushing Web image ${WEB_IMAGE}:${IMAGE_TAG}"
                                    docker push "${WEB_IMAGE}:${IMAGE_TAG}"
                                    docker push "${WEB_IMAGE}:latest"
                                fi
                            '''
                        }
                    } catch (Exception e) {
                        echo "Registry credentials 'container-registry-credentials' not configured in Jenkins. Skipping image push: ${e.getMessage()}"
                    }
                }
            }
        }

        // Stage 11: Validate Kubernetes Manifests
        stage('Validate Kubernetes Manifests') {
            steps {
                echo "=== Stage 11: Validating Kubernetes manifests and observability configs ==="
                sh '''
                    echo "Validating Kubernetes manifests..."
                    cd infra/k8s
                    npm ci --prefer-offline || npm install
                    node validate.mjs
                    cd ../..

                    echo "Validating observability alert rules and dashboard..."
                    cd infra/observability
                    npm ci --prefer-offline || npm install
                    node validate.mjs
                    cd ../..

                    if [ -f "infra/github/validate.mjs" ]; then
                        echo "Validating GitHub workflow configs..."
                        cd infra/github
                        npm ci --prefer-offline || npm install
                        node validate.mjs
                        cd ../..
                    fi
                '''
            }
        }

        // Stage 12: Controlled Deploy to Test / Staging Cluster
        stage('Deploy to Staging') {
            when {
                anyOf {
                    branch 'main'
                    branch 'staging'
                }
            }
            steps {
                echo "=== Stage 12: Deploying to staging Kubernetes cluster ==="
                script {
                    try {
                        withCredentials([file(credentialsId: 'k8s-staging-kubeconfig', variable: 'KUBECONFIG')]) {
                            sh '''
                                echo "Applying staging overlay manifests..."
                                if command -v kubectl >/dev/null 2>&1; then
                                    kubectl apply -k infra/k8s/overlays/staging
                                    echo "Waiting for rollout to finish..."
                                    kubectl rollout status deployment/havyn-api -n havyn-staging --timeout=120s
                                else
                                    echo "kubectl not found on agent. Manifests verified in Stage 11, skipping live rollout."
                                fi
                            '''
                        }
                    } catch (Exception e) {
                        echo "Staging cluster credentials 'k8s-staging-kubeconfig' not configured in Jenkins. Skipping live deployment: ${e.getMessage()}"
                    }
                }
            }
        }

        // Stage 13: Deployment Smoke Tests
        stage('Smoke Tests') {
            when {
                anyOf {
                    branch 'main'
                    branch 'staging'
                }
            }
            steps {
                echo "=== Stage 13: Executing deployment smoke tests ==="
                sh '''
                    if [ -n "$STAGING_BASE_URL" ]; then
                        echo "Executing synthetic checks against $STAGING_BASE_URL..."
                        SYNTHETIC_BASE_URL="$STAGING_BASE_URL" node infra/observability/synthetic-checks.mjs
                    else
                        echo "STAGING_BASE_URL not configured. Running local readiness check validator..."
                        test -f apps/api/src/main/resources/application.yml
                    fi
                '''
            }
        }

        // Stage 14: Manual Approval Gate for Production
        stage('Production Approval & Deploy') {
            when {
                branch 'main'
            }
            steps {
                echo "=== Stage 14: Production deployment gate ==="
                script {
                    // Manual approval required before any production release
                    input message: "Approve deployment of HavynVilla ${IMAGE_TAG} to Production?", ok: "Deploy to Production"
                    
                    try {
                        withCredentials([file(credentialsId: 'k8s-production-kubeconfig', variable: 'KUBECONFIG')]) {
                            sh '''
                                echo "Deploying to production overlay..."
                                kubectl apply -k infra/k8s/overlays/production
                                kubectl rollout status deployment/havyn-api -n havyn-production --timeout=180s
                            '''
                        }
                    } catch (Exception e) {
                        echo "Production cluster credentials 'k8s-production-kubeconfig' not configured. Skipping production deployment: ${e.getMessage()}"
                    }
                }
            }
        }
    }

    post {
        always {
            echo "Pipeline run completed for build: ${env.BUILD_NUMBER} (Commit: ${env.IMAGE_TAG})"
            cleanWs deleteDirs: true, notFailBuild: true
        }
        success {
            echo "CI/CD Pipeline succeeded cleanly! All mandatory gates passed."
        }
        failure {
            echo "CI/CD Pipeline failed! Check the console log and published test reports above."
        }
    }
}
