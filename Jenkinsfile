pipeline {
    agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        timeout(time: 10, unit: 'MINUTES')
        timestamps()  // Timestamper Plugin
        disableConcurrentBuilds()
        skipStagesAfterUnstable()
    }
    environment {
        COMPANY = 'Puzzle ITC'
    }
    parameters {
        string(name: 'smile', defaultValue: ':)', description: 'just smile')
    }
    tools {
        jdk 'jdk11'
        maven 'maven36'
    }
    stages {
        stage('Info') {
            steps {
                def SMILE = ':)'
                echo "Running ${env.BUILD_ID} on ${env.JENKINS_URL} for ${env.COMPANY} ${params.smile}"
            }
        }
        stage('Build') {
            steps {
                withMaven { // Requires Pipeline Maven Integration plugin
                    sh 'mvn -B -Pdefault,deb -DskipTests clean package -Dsurefire.useFile=false -DargLine="-Djdk.net.URLClassPath.disableClassPathURLCheck=true"'
                }
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
        stage('Deploy') {
            when {
              expression {
                currentBuild.result == null || currentBuild.result == 'SUCCESS'
              }
            }
            steps {
                echo 'Deploying..'
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
        }
    }
}