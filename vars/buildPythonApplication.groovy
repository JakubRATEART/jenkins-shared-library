// vars/buildPythonApplication.groovy
def call(Map config = [:]) {
    // Read the inputs handed over by the application repo
    def appName = config.appName ?: 'default-app'
    def internalPort = config.port ?: '8080'

    pipeline {
        agent any

        stages {
            stage('Test') {
                steps {
                    script {
                        // The library handles containerizing the tests safely
                        docker.image('python:3.11-slim').inside {
                            echo "Checking python syntax for ${appName}..."
                            sh 'python3 -m py_compile *.py || true'
                        }
                    }
                }
            }

            stage('Build Image') {
                steps {
                    echo "Building Docker image: ${appName}:latest..."
                    sh "docker build -t ${appName}:latest ."
                }
            }

            stage('Deploy') {
                steps {
                    script {
                        // Dynamically compute the staging vs production ports in one place!
                        def containerName = "app-${BRANCH_NAME}-${appName}"
                        def hostPort = (BRANCH_NAME == 'main') ? '8000' : '8001'

                        echo "Deploying ${containerName} to host port ${hostPort}..."
                        sh "docker stop ${containerName} || true"
                        sh "docker rm ${containerName} || true"
                        sh "docker run -d --name ${containerName} -p ${hostPort}:${internalPort} ${appName}:latest"
                    }
                }
            }
        }

        post {
            always {
                cleanWs()
            }
        }
    }
}
