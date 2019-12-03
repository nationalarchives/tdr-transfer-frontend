pipeline {
  agent none

  stages {
    stage('Test') {
        agent {
            ecs {
                inheritFrom 'ecs'
            }
        }
        steps {
            checkout scm
            sh 'sbt test'
            sh 'sbt dist'
            stash includes: 'target/universal/tdr-transfer-frontend-1.0-SNAPSHOT.zip', name: 'tdr-transfer-frontend-1.0-SNAPSHOT.zip'
        }
    }
    stage('Docker') {
            agent {
                label 'master'
            }
            steps {
                checkout scm
                unstash 'tdr-transfer-frontend-1.0-SNAPSHOT.zip'
                sh 'ls -la'
                sh 'docker build -t nationalarchives/tdr-transfer-frontend .'
                withCredentials([usernamePassword(credentialsId: 'docker', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    sh 'echo $PASSWORD | docker login --username $USERNAME --password-stdin'
                }
            }
        }
  }
}
