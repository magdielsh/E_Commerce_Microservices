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

@Library('spring-microservices-lib') _

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
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    // ------------------------------------------------------------
    // ENVIRONMENT: variables globales visibles en todos los stages
    // ------------------------------------------------------------
    environment {
        // Registry donde subiremos las imágenes. Si usas Docker Hub,
        // sería algo como "tuusuario". Si usas uno privado, la URL completa.
        REGISTRY = "magdielsh"

        IMAGE_TAG = "${env.BUILD_NUMBER}"

        // Nombre que le daremos a la imagenes de Docker resultantes
        IMAGE_NAME_EUREKA = "eureka-server"

        IMAGE_NAME_GATEWAY = "gateway"

        IMAGE_NAME_ACCOUNT = "account-service"

        IMAGE_NAME_ORDER = "order-service"

        IMAGE_NAME_PRODUCT = "product-service"

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
                    def previousCommit = env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: sh(script: 'git rev-parse HEAD~1', returnStdout: true).trim()
                    def changedFiles = sh(
                        script: "git diff --name-only ${previousCommit} HEAD",
                        returnStdout: true
                    ).trim()

                    env.ACCOUNT_CHANGED    = changedFiles.contains('account-service/')    ? 'true' : 'false'
                    env.EUREKA_CHANGED = changedFiles.contains('eureka-server/') ? 'true' : 'false'
                    env.GATEWAY_CHANGED  = changedFiles.contains('gateway/')  ? 'true' : 'false'
                    env.ORDER_CHANGED = changedFiles.contains('order-service/') ? 'true' : 'false'
                    env.PRODUCTS_CHANGED  = changedFiles.contains('product-service/')  ? 'true' : 'false'

                    echo "Account-Service cambió, se compilara Nuevamente: ${env.ACCOUNT_CHANGED}"
                    echo "Eureka-Server, se compilara Nuevamente: ${env.EUREKA_CHANGED}"
                    echo "Gateway cambió, se compilara Nuevamente: ${env.GATEWAY_CHANGED}"
                    echo "Order-Service cambió, se compilara Nuevamente: ${env.ORDER_CHANGED}"
                    echo "Product-Service cambió, se compilara Nuevamente: ${env.PRODUCTS_CHANGED}"

                    //                    if (changedFiles.contains('account-service/')){
                    //                        env.ACCOUNT_CHANGED = 'true'
                    //                        echo "Account-Service cambio, se compilara Nuevamente"
                    //                    }
                    //                    if (changedFiles.contains('eureka-server/')){
                    //                        env.EUREKA_CHANGED = 'true'
                    //                        echo "Eureka-Server cambio, se compilara Nuevamente"
                    //                    }
                    //
                    //                    if (changedFiles.contains('gateway/')){
                    //                        env.GATEWAY_CHANGED = 'true'
                    //                        echo "Gateway cambio, se compilara Nuevamente"
                    //                    }
                    //
                    //                    if (changedFiles.contains('order-service/')){
                    //                        env.ORDER_CHANGED = 'true'
                    //                        echo "Order-Service cambio, se compilara Nuevamente"
                    //                    }
                    //
                    //                    if (changedFiles.contains('product-service/')){
                    //                        env.PRODUCTS_CHANGED = 'true'
                    //                        echo "Product-Service cambio, se compilara Nuevamente"
                    //                    }
                }
            }
        }

        // ==========================================================
        // STAGE 2: Build + Test en PARALELO para todos los servicios
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
                        build_test_SpringService(servicePath: 'gateway', runTests: false)
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
                        build_test_SpringService(servicePath: 'eureka-server', runTests: false)
                    }
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
                        build_test_SpringService(servicePath: 'order-service', runTests: true)
                    }
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
                        build_test_SpringService(servicePath: 'product-service', runTests: false)
                    }
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
                        build_test_SpringService(servicePath: 'account-service', runTests: false)
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // STAGE 4: Empaquetar (genera el .jar ejecutable)
        // ---------------------------------------------------------
        stage('Package') {
            agent any
            when {
                expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            }
            steps {
                script{
                    if(env.GATEWAY_CHANGED == 'true'){
                        package_SpringService(servicePath: 'gateway')
                    }
                    if(env.EUREKA_CHANGED == 'true'){
                        package_SpringService(servicePath: 'eureka-server')
                    }
                    if(env.ORDER_CHANGED == 'true'){
                        package_SpringService(servicePath: 'order-service')
                    }
                    if (env.PRODUCTS_CHANGED == 'true') {
                        package_SpringService(servicePath: 'product-service')
                    }
                    if (env.ACCOUNT_CHANGED == 'true') {
                        package_SpringService(servicePath: 'account-service')
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // STAGE 5: Construir imagen Docker
        // ---------------------------------------------------------
        stage('Docker Build') {
            agent any
            when {
                expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            }
            steps {
                script{
                    if(env.GATEWAY_CHANGED == 'true'){
                        build_DockerService(servicePath: 'gateway', imageName: "${IMAGE_NAME_GATEWAY}")
                    }
                    if(env.EUREKA_CHANGED == 'true'){
                        build_DockerService(servicePath: 'eureka-server', imageName: "${IMAGE_NAME_EUREKA}")
                    }
                    if(env.ORDER_CHANGED == 'true'){
                        build_DockerService(servicePath: 'order-service', imageName: "${IMAGE_NAME_ORDER}")
                    }
                    if (env.PRODUCTS_CHANGED == 'true') {
                        build_DockerService(servicePath: 'product-service', imageName: "${IMAGE_NAME_PRODUCT}")
                    }
                    if (env.ACCOUNT_CHANGED == 'true') {
                        build_DockerService(servicePath: 'account-service', imageName: "${IMAGE_NAME_ACCOUNT}")
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
            when {
                expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            }
            steps {
                script{
                    // Solo hacemos deploy de la imagen del servicio que
                    // realmente cambió (o si es la primera vez, ambos)
                    if (env.GATEWAY_CHANGED == 'true') {
                        deploy_DockerService(
                            servicePath: 'gateway',
                            imageName: "${IMAGE_NAME_GATEWAY}",
                            jwtSecret: true,
                            jenkinsNet: "${NETWORK}",
                            imagePort: '7080'
                        )
                    }
                    if (env.EUREKA_CHANGED == 'true') {
                        deploy_DockerService(
                            servicePath: 'eureka-server',
                            imageName: "${IMAGE_NAME_EUREKA}",
                            jwtSecret: false,
                            jenkinsNet: "${NETWORK}",
                            imagePort: '8761'
                        )
                    }
                    if (env.PRODUCTS_CHANGED == 'true') {
                        deploy_DockerService(
                            servicePath: 'products-service',
                            imageName: "${IMAGE_NAME_PRODUCT}",
                            jwtSecret: false,
                            jenkinsNet: "${NETWORK}",
                            imagePort: '7095'
                        )
                    }
                    if(env.ORDER_CHANGED == 'true'){
                        deploy_DockerService(
                            servicePath: 'order-service',
                            imageName: "${IMAGE_NAME_ORDER}",
                            jwtSecret: false,
                            jenkinsNet: "${NETWORK}",
                            imagePort: '7198'
                        )
                        //                        sh  """
                        //                          docker stop order-service || true
                        //                          docker rm order-service || true
                        //                          docker run -d \
                        //                            --name order-service \
                        //                            --network ${NETWORK} \
                        //                            -p 7198:7198 \
                        //                            ${IMAGE_NAME_ORDER}:latest
                        //                           """
                    }
                    if(env.ACCOUNT_CHANGED == 'true'){
                        deploy_DockerService(
                            servicePath: 'account-service',
                            imageName: "${IMAGE_NAME_ACCOUNT}",
                            jwtSecret: true,
                            jenkinsNet: "${NETWORK}",
                            imagePort: '6589'
                        )
                        //                        withCredentials([string(credentialsId: 'jwt_secret', variable: 'JWT')]) {
                        //                            sh  '''
                        //                                  docker stop account-service || true
                        //                                  docker rm account-service || true
                        //                                  docker run -d \
                        //                                    --name account-service \
                        //                                    --network ${NETWORK} \
                        //                                    -e JWT_SECRET="${JWT}" \
                        //                                    -p 6589:6589 \
                        //                                    ${IMAGE_NAME_ACCOUNT}:latest
                        //                           '''
                        //                        }
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
            notify_BuildResult(recipients: 'magdielsh30@gmail.com')
            //            emailext (
            //                subject: "✅ SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            //                body: '${SCRIPT, template="groovy-html.template"}',
            //                to: 'magdielsh30@gmail.com',
            //                attachLog: true,          // adjunta el console log completo
            //                compressLog: true         // lo comprime en .gz si es grande
            //            )
        }
        unstable {
            echo "⚠️ (Unstable) Hay cosas que han fallado. Revisa los logs del stage que falló."
            notify_BuildResult(recipients: 'magdielsh30@gmail.com')
        }unsuccessful {
            echo "⚠️ (Unsuccessful) Hay cosas que han fallado. Revisa los logs del stage que falló."
            notify_BuildResult(recipients: 'magdielsh30@gmail.com')
        }
        failure {
            echo "❌ El pipeline ha fallado. Revisa los logs del stage que falló."
            notify_BuildResult(recipients: 'magdielsh30@gmail.com')
        }
        always {
            cleanWs()
        }
    }
}