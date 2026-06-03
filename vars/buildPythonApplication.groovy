// vars/buildPythonApplication.groovy
// Inside vars/buildPythonApplication.groovy
def call(Map config = [:]) {
    def appName = config.appName ?: 'default-app'
    def internalPort = config.port ?: '8080'

    pipeline {
        agent any

        stages {
            stage('Test') {
                steps {
                    script {
                        // The Python container ONLY lives for this specific test block!
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
                        // Notice: NO docker.inside block here! We run directly on the host agent.
                        def currentBranch = env.BRANCH_NAME
                        def containerName = "app-${currentBranch}-${appName}"
                        def hostPort = (currentBranch == 'main') ? '8000' : '8001'

                        echo "Deploying ${containerName} to host port ${hostPort}..."
                        sh "docker stop ${containerName} || true"
                        sh "docker rm ${containerName} || true"
			sh "docker run -d --name ${containerName} -p ${hostPort}:${internalPort} ${appName}:latest"
			echo "Verifying API availability on port ${hostport}"
			sh "sleep 5"
			sh "curl --fail http://localhost:${hostPort}/api/status || (docker logs ${containerName} && exit 1)"
			
                    }
                }
            }
        }

        post {
            always {
                echo 'Cleaning up workspace...'
                cleanWs()
            }
        }
    }
}
