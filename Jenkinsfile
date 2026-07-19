// Jenkinsfile
//
// Este archivo NO va en el proyecto "jenkins-local-setup".
// Va DENTRO del repositorio de tu proyecto Spring Boot (en la raíz,
// junto al pom.xml). Esto es "Pipeline as Code": la definición del
// proceso de CI/CD vive versionada junto a tu código, no se configura
// a mano en la interfaz web de Jenkins.
//
// Sintaxis: "Declarative Pipeline" (la forma moderna y recomendada,
// frente a la sintaxis antigua "Scripted Pipeline" basada en Groovy puro)

pipeline {

    // agent any = este pipeline puede ejecutarse en cualquier agente
    // disponible (en tu caso, el propio Jenkins, ya que no tienes
    // agentes remotos configurados)
    agent any

    // ------------------------------------------------------------
    // OPTIONS: comportamiento general del pipeline
    // ------------------------------------------------------------
    options {
        // Si el pipeline entero tarda más de 20 minutos, Jenkins lo mata.
        // Evita que un build colgado (ej. un test que nunca termina)
        // bloquee el agente indefinidamente.
        timeout(time: 20, unit: 'MINUTES')

        // Añade marca de tiempo a cada línea del log de la consola.
        // Muy útil para depurar cuánto tarda cada stage.
        timestamps()

        // Si haces 2 pushes seguidos muy rápido, evita que 2 builds del
        // MISMO job corran en paralelo (podrían pisarse el despliegue).
        disableConcurrentBuilds()

        // Solo conserva el historial de los últimos 15 builds, para no
        // llenar el disco de logs y artefactos viejos.
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    // ------------------------------------------------------------
    // ENVIRONMENT: variables globales visibles en todos los stages
    // ------------------------------------------------------------
    environment {
        // Registry donde subiremos las imágenes. Si usas Docker Hub,
        // sería algo como "tuusuario". Si usas uno privado, la URL completa.
        REGISTRY = "magdielsh"

        JWT_SECRET = credentials('jwt_secret')

        // Usamos el número de build como tag, para que cada imagen sea
        // trazable a un build concreto de Jenkins (útil para hacer rollback:
        // "vuelve a desplegar la imagen del build 42").
        IMAGE_TAG = "${env.BUILD_NUMBER}"

        // Nombre que le daremos a la imagenes de Docker resultantes
        IMAGE_NAME_EUREKA = "eureka-server-0.0.1"

        IMAGE_NAME_GATEWAY = "gateway-0.0.1"

        IMAGE_NAME_ACCOUNT = "account-service"

        IMAGE_NAME_ORDER = "order-service-0.0.1"

        IMAGE_NAME_PRODUCT = "product-service-0.0.1"

        // Nombre de la red Docker donde correrán los contenedores, para que
        // order-service pueda resolver "products-service" por nombre DNS
        // en vez de por IP (Docker crea DNS interno automático por nombre
        // de contenedor dentro de la misma red).
        NETWORK = "jenkins-net"
    }

    // Herramientas que Jenkins debe tener configuradas globalmente
    // (Manage Jenkins > Tools). El nombre "maven3" debe coincidir
    // EXACTAMENTE con el nombre que le des en esa configuración.
    tools {
        maven 'maven3'
        jdk 'jdk17'
    }

    stages {

        // ---------------------------------------------------------
        // STAGE 1: Checkout
        // ---------------------------------------------------------
        // Jenkins ya hizo el clonado automáticamente si configuraste
        // el job como "Pipeline script from SCM", pero lo dejamos
        // explícito para que veas que este paso existe.
        stage('Checkout') {
            steps {
                checkout scm
                echo "Código descargado. Commit actual:"
                sh 'git log -1 --oneline'
            }
        }

        // ==========================================================
        // STAGE 1.1 Detectar qué cambió
        // ==========================================================
        // En un monorepo con 2 microservicios, NO tiene sentido reconstruir
        // ambos si solo tocaste uno. Este stage decide qué construir.
        stage('Detectar cambios') {
            steps {
                script {
                    // "changeset" comprueba si algún archivo modificado en
                    // el último commit/push cae dentro de esa carpeta.
                    // Guardamos el resultado (true/false) en variables que
                    // usaremos después en los "when" de cada stage.
                    env.ACCOUNT_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 HEAD | grep -q '^account-service/' && echo true || echo false",
                        returnStdout: true
                    ).trim()
                    env.GATEWAY_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 HEAD | grep -q '^gateway/' && echo true || echo false",
                        returnStdout: true
                    ).trim()

                    env.EUREKA_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 HEAD | grep -q '^eureka-server/' && echo true || echo false",
                        returnStdout: true
                    ).trim()

                    env.ORDER_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 HEAD | grep -q '^order-service/' && echo true || echo false",
                        returnStdout: true
                    ).trim()

                    env.PRODUCTS_CHANGED = sh(
                        script: "git diff --name-only HEAD~1 HEAD | grep -q '^product-service/' && echo true || echo false",
                        returnStdout: true
                    ).trim()

                    // echo simplemente imprime en el log de consola, para
                    // que puedas ver de un vistazo qué se va a construir.
                    if (env.ACCOUNT_CHANGED == 'true'){
                        echo "Account-Service cambio, se compilara Nuevamente"
                    }
                    if (env.EUREKA_CHANGED == 'true'){
                        echo "Eureka-Server cambio, se compilara Nuevamente"
                    }

                    if (env.GATEWAY_CHANGED == 'true'){
                        echo "Gateway cambio, se compilara Nuevamente"
                    }

                    if (env.ORDER_CHANGED == 'true'){
                        echo "Order-Service cambio, se compilara Nuevamente"
                    }

                    if (env.PRODUCTS_CHANGED == 'true'){
                        echo "Product-Service cambio, se compilara Nuevamente"
                    }
                }
            }
        }

        // ==========================================================
        // STAGE 2: Build + Test en PARALELO para ambos servicios
        // ==========================================================
        // "parallel" hace que los dos bloques internos corran a la vez,
        // no uno detrás del otro. Ahorra tiempo cuando ambos cambiaron.
        stage('Build & Test') {
            parallel {

                // -------- Gateway --------
                stage('gateway') {
                    // "when" hace que este stage entero se salte si la
                    // condición es falsa. expression{} evalúa código Groovy.
                    when {
                        expression { env.GATEWAY_CHANGED == 'true' }
                    }
                    // Este stage corre DENTRO de un contenedor Docker con
                    // Maven y JDK 17 ya instalados. Jenkins lo levanta,
                    // ejecuta los "steps", y lo destruye al terminar.
                    // Ventaja sobre "tools{}": es 100% reproducible, no
                    // depende de qué tenga instalado el agente Jenkins.
                    agent {
                        docker {
                            image 'maven:3.9-eclipse-temurin-17'
                            // Reusa el .m2 local del host como caché, para
                            // no descargar TODAS las dependencias Maven en
                            // cada build (que sería lentísimo)
                            args '-v $HOME/.m2:/root/.m2'
                        }
                    }
                    steps {
                        // "dir()" cambia el directorio de trabajo solo para
                        // los comandos que están dentro de sus llaves.
                        dir('gateway') {
                            // -DskipTests Compila sin correr tests todavía (los tests
                            // van en el siguiente sh, para separar reportes)
                            sh 'mvn clean compile -DskipTests'

                            // Aquí corren tus tests con WireMock: los stubs
                            // que verifican Feign + Resilience4j (retries,
                            // circuit breaker) se ejecutan en este paso,
                            // sin necesidad de que products-service real
                            // esté levantado.
                            //sh 'mvn test'
                        }
                    }
                    //                    post {
                    //                        // "always" se ejecuta pase lo que pase (tests OK o KO).
                    //                        // junit publica los resultados en un reporte visual
                    //                        // dentro de Jenkins (pestaña "Test Result")
                    //                        // Publica el reporte de tests SOLO para este stage,
                    //                        // apuntando a la ruta dentro de order-service/
                    //                        always {
                    //                            junit 'order-service/target/surefire-reports/*.xml'
                    //                        }
                    //                    }
                }

                // -------- Eureka-Server --------
                stage('eureka-server') {
                    when {
                        expression { env.EUREKA_CHANGED == 'true' }
                    }
                    agent {
                        docker {
                            image 'maven:3.9-eclipse-temurin-17'
                            args '-v $HOME/.m2:/root/.m2'
                        }
                    }
                    steps {
                        dir('eureka-server') {
                            sh 'mvn clean compile -DskipTests'
                            //sh 'mvn test'
                        }
                    }
                    //                    post {
                    //                        always {
                    //                            junit 'order-service/target/surefire-reports/*.xml'
                    //                        }
                    //                    }
                }

                // -------- order-service --------
                stage('order-service') {
                    when {
                        expression { env.ORDER_CHANGED == 'true' }
                    }
                    agent {
                        docker {
                            image 'maven:3.9-eclipse-temurin-17'
                            args '-v $HOME/.m2:/root/.m2'
                        }
                    }
                    steps {
                        dir('order-service') {
                            sh 'mvn clean compile -DskipTests'
                            //sh 'mvn test'
                        }
                    }
//                    post {
//                        always {
//                            junit 'order-service/target/surefire-reports/*.xml'
//                        }
//                    }
                }

                // -------- products-service --------
                stage('product-service') {
                    when {
                        expression { env.PRODUCTS_CHANGED == 'true' }
                    }
                    agent {
                        docker {
                            image 'maven:3.9-eclipse-temurin-17'
                            args '-v $HOME/.m2:/root/.m2'
                        }
                    }
                    steps {
                        dir('product-service') {
                            sh 'mvn clean compile -DskipTests'
                            //sh 'mvn test'
                        }
                    }
//                    post {
//                        always {
//                            junit 'products-service/target/surefire-reports/*.xml'
//                        }
//                    }
                }

                // -------- account-service --------
                stage('account-service') {
                    when {
                        expression { env.ACCOUNT_CHANGED == 'true' }
                    }
                    agent {
                        docker {
                            image 'maven:3.9-eclipse-temurin-17'
                            args '-v $HOME/.m2:/root/.m2'
                        }
                    }
                    steps {
                        dir('account-service') {
                            sh 'mvn clean compile -DskipTests'
                            //sh 'mvn test'
                        }
                    }
                    //                    post {
                    //                        always {
                    //                            junit 'products-service/target/surefire-reports/*.xml'
                    //                        }
                    //                    }
                }
            }
        }

        // ---------------------------------------------------------
        // STAGE 4: Empaquetar (genera el .jar ejecutable)
        // ---------------------------------------------------------
        stage('Package') {
            agent any
            steps {
                script{
                    // Solo empaquetamos/construimos imagen del servicio que
                    // realmente cambió (o si es la primera vez, ambos)
                    if(env.GATEWAY_CHANGED == 'true'){
                        dir('gateway'){
                            sh 'mvn package -DskipTests'
                            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                        }
                    }
                    if(env.EUREKA_CHANGED == 'true'){
                        dir('eureka-server'){
                            sh 'mvn package -DskipTests'
                            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                        }
                    }
                    if(env.ORDER_CHANGED == 'true'){
                        dir('order-service'){
                            sh 'mvn package -DskipTests'
                            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                        }
                    }
                    if (env.PRODUCTS_CHANGED == 'true') {
                        dir('product-service') {
                            sh 'mvn package -DskipTests'
                            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                        }
                    }
                    if (env.ACCOUNT_CHANGED == 'true') {
                        dir('account-service') {
                            sh 'mvn package -DskipTests'
                            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                        }
                    }
                }
            }
//            post {
//                success {
//                    // Guarda el .jar como "artefacto" del build, así
//                    // puedes descargarlo directamente desde la UI de
//                    // Jenkins sin tener que ir a buscarlo a mano
//                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
//                }
//            }
        }

        // ---------------------------------------------------------
        // STAGE 5: Construir imagen Docker
        // ---------------------------------------------------------
        stage('Docker Build') {
            agent any
            steps {
                script{
                    // Solo construimos imagen del servicio que
                    // realmente cambió (o si es la primera vez, ambos)
                    if(env.GATEWAY_CHANGED == 'true'){
                        dir('gateway'){
                            sh "docker build -t ${IMAGE_NAME_GATEWAY}:${IMAGE_TAG} ."
                            sh "docker tag ${IMAGE_NAME_GATEWAY}:${IMAGE_TAG} ${IMAGE_NAME_GATEWAY}:latest"
                        }
                    }
                    if(env.EUREKA_CHANGED == 'true'){
                        dir('eureka-server'){
                            sh "docker build -t ${IMAGE_NAME_EUREKA}:${IMAGE_TAG} ."
                            sh "docker tag ${IMAGE_NAME_EUREKA}:${IMAGE_TAG} ${IMAGE_NAME_EUREKA}:latest"
                        }
                    }
                    if(env.ORDER_CHANGED == 'true'){
                        dir('order-service'){
                            sh "docker build -t ${IMAGE_NAME_ORDER}:${IMAGE_TAG} ."
                            sh "docker tag ${IMAGE_NAME_ORDER}:${IMAGE_TAG} ${IMAGE_NAME_ORDER}:latest"
                        }
                    }
                    if (env.PRODUCTS_CHANGED == 'true') {
                        dir('product-service') {
                            sh "docker build -t ${IMAGE_NAME_PRODUCT}:${IMAGE_TAG} ."
                            sh "docker tag ${IMAGE_NAME_PRODUCT}:${IMAGE_TAG} ${IMAGE_NAME_PRODUCT}:latest"
                        }
                    }
                    if (env.ACCOUNT_CHANGED == 'true') {
                        dir('account-service') {
                            sh "docker build -t ${IMAGE_NAME_ACCOUNT}:${IMAGE_TAG} ."
                            sh "docker tag ${IMAGE_NAME_ACCOUNT}:${IMAGE_TAG} ${IMAGE_NAME_ACCOUNT}:latest"
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // STAGE 6: Desplegar SOLO si estamos en main
        // ---------------------------------------------------------
        // En un entorno real esto normalmente sería:
        //  - Push a un registry (Docker Hub, ECR, GitLab Registry...)
        //  - kubectl apply / helm upgrade si usas Kubernetes
        //  - Ansible/SSH si despliegas sobre VMs
        // Para tu entorno LOCAL, simplemente paramos el contenedor
        // anterior (si existe) y levantamos el nuevo.
        stage('Deploy'){
//            when {
//                branch 'main'
//            }
            steps {
                script{
                    // Solo hacemos deploy de la imagen del servicio que
                    // realmente cambió (o si es la primera vez, ambos)
                    if (env.GATEWAY_CHANGED == 'true') {
                        withCredentials([string(credentialsId: 'jwt_secret',
                                    variable: 'JWT')]) {
                            echo '*** EXPONIENDO JWT: ${JWT} ***'
                        }
                        sh """
                          docker stop gateway || true
                          docker rm gateway || true
                          docker run -d \
                            --name gateway \
                            --network ${NETWORK} \
                            -e JWT_SECRET=${JWT_SECRET} \
                            -p 7080:7080 \
                            ${IMAGE_NAME_GATEWAY}:latest
                           """
                    }
                    if (env.EUREKA_CHANGED == 'true') {
                        sh """
                          docker stop eureka-server || true
                          docker rm eureka-server || true
                          docker run -d \
                            --name eureka-server \
                            --network ${NETWORK} \
                            -p 8761:8761 \
                            ${IMAGE_NAME_EUREKA}:latest
                           """
                    }
                    if (env.PRODUCTS_CHANGED == 'true') {
                       sh """
                          docker stop products-service || true
                          docker rm products-service || true
                          docker run -d \
                            --name products-service \
                            --network ${NETWORK} \
                            -p 7095:7095 \
                            ${IMAGE_NAME_PRODUCT}:latest
                           """
                    }
                    if(env.ORDER_CHANGED == 'true'){
                     sh  """
                          docker stop order-service || true
                          docker rm order-service || true
                          docker run -d \
                            --name order-service \
                            --network ${NETWORK} \
                            -p 7198:7198 \
                            ${IMAGE_NAME_ORDER}:latest
                           """
                    }
                    if(env.ACCOUNT_CHANGED == 'true'){
                        sh  """
                          docker stop account-service || true
                          docker rm account-service || true
                          docker run -d \
                            --name account-service \
                            --network ${NETWORK} \
                            -p 6589:6589 \
                            ${IMAGE_NAME_ACCOUNT}:latest
                           """
                    }


                }
            }
        }
    }

    // Acciones que se ejecutan al finalizar el pipeline, independientemente
    // del resultado (éxito, fallo, inestable...)
    post {
        success {
            echo "✅ Pipeline completado con éxito. App desplegada"
        }
        failure {
            echo "❌ El pipeline ha fallado. Revisa los logs del stage que falló."
        }
        always {
            // Limpia el workspace para no acumular basura entre builds
            cleanWs()
        }
    }
}