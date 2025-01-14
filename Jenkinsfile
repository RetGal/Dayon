pipeline {
    agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        timeout(time: 10, unit: 'MINUTES')
        timestamps()  // Timestamper Plugin
        disableConcurrentBuilds()
        skipStagesAfterUnstable() // instead of currentBuild.currentResult == 'SUCCESS'
        throttleJobProperty(
            categories: ['multiBranch'],
            throttleEnabled: true,
            throttleOption: 'category'
        )
    }
    environment {
        COMPANY = 'Puzzle ITC'
        smile = '(:'
    }
    parameters {
        string(name: 'smile', defaultValue: ':)', description: 'just smile')
    }
//     tools {
//         jdk 'jdk11'
//         maven 'maven36'
//     }
    stages {
        stage('Info') {
            steps {
                echo "Running ${env.BUILD_ID} on ${env.JENKINS_URL} for ${env.COMPANY}"
                echo "Smile params: ${params.smile} env: ${env.smile} no prefix: ${smile}"
            }
        }
        stage('Build') {
            steps {
                sh 'mvn -Pdefault,deb -DskipTests clean package -Dsurefire.useFile=false -DargLine="-Djdk.net.URLClassPath.disableClassPathURLCheck=true"'
            }
        }
        stage('Test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
//         stage('Human sanity check') {
//             steps {
//                 input "Does everything look fine?"
//             }
//         }
        stage('Deploy') {
            steps {
                echo 'Deploying..'
                sh 'ls -asl'
            }
        }
    }
    post {
        success {
            echo 'Success'
        }
        failure {
            echo 'Failure'
        }
        unstable {
            echo 'Unstable'
        }
        changed {
            echo 'Changed'
        }
        always {
            echo 'Done'
            deleteDir() // clean up our workspace
        }
    }
}