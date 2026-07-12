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

    // Herramientas que Jenkins debe tener configuradas globalmente
    // (Manage Jenkins > Tools). El nombre "maven3" debe coincidir
    // EXACTAMENTE con el nombre que le des en esa configuración.
    tools {
        maven 'maven3'
        jdk 'jdk17'
    }

    // Variables de entorno disponibles en todos los "stages"
    environment {
        // Nombre que le daremos a la imagen Docker resultante
        IMAGE_NAME = "mi-app-spring-boot"
        // Usamos el número de build de Jenkins como tag de la imagen,
        // así cada build genera una imagen distinta y trazable
        IMAGE_TAG  = "${env.BUILD_NUMBER}"
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

        // ---------------------------------------------------------
        // STAGE 2: Compilar
        // ---------------------------------------------------------
        stage('Build') {
            steps {
                // -DskipTests porque los tests los corremos en su
                // propio stage, separado, para que el reporte quede
                // más claro en la interfaz de Jenkins
                sh 'mvn clean compile -DskipTests'
            }
        }

        // ---------------------------------------------------------
        // STAGE 3: Tests
        // ---------------------------------------------------------
//         stage('Test') {
//             steps {
//                 sh 'mvn test'
//             }
//             post {
//                 // "always" se ejecuta pase lo que pase (tests OK o KO).
//                 // junit publica los resultados en un reporte visual
//                 // dentro de Jenkins (pestaña "Test Result")
//                 always {
//                     junit 'target/surefire-reports/*.xml'
//                 }
//             }
//         }

        // ---------------------------------------------------------
        // STAGE 4: Empaquetar (genera el .jar ejecutable)
        // ---------------------------------------------------------
        stage('Package') {
            steps {
                sh 'mvn package -DskipTests'
            }
            post {
                success {
                    // Guarda el .jar como "artefacto" del build, así
                    // puedes descargarlo directamente desde la UI de
                    // Jenkins sin tener que ir a buscarlo a mano
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }
            }
        }

        // ---------------------------------------------------------
        // STAGE 5: Construir imagen Docker
        // ---------------------------------------------------------
        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                // También la etiquetamos como "latest" para tener
                // siempre una referencia fija a la última versión
                sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest"
            }
        }

        // ---------------------------------------------------------
        // STAGE 6: Desplegar (versión local sencilla)
        // ---------------------------------------------------------
        // En un entorno real esto normalmente sería:
        //  - Push a un registry (Docker Hub, ECR, GitLab Registry...)
        //  - kubectl apply / helm upgrade si usas Kubernetes
        //  - Ansible/SSH si despliegas sobre VMs
        // Para tu entorno LOCAL, simplemente paramos el contenedor
        // anterior (si existe) y levantamos el nuevo.
        stage('Deploy') {
            steps {
                sh '''
                    docker stop mi-app-spring-boot || true
                    docker rm mi-app-spring-boot || true
                    docker run -d \
                        --name mi-app-spring-boot \
                        --network jenkins-net \
                        -p 8081:8080 \
                        ${IMAGE_NAME}:${IMAGE_TAG}
                '''
            }
        }
    }

    // Acciones que se ejecutan al finalizar el pipeline, independientemente
    // del resultado (éxito, fallo, inestable...)
    post {
        success {
            echo "✅ Pipeline completado con éxito. App desplegada en http://localhost:8081"
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