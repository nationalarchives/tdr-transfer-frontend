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
        }
    }
    stage('Docker') {
            agent {
                label 'master'
            }
            steps {
                checkout scm
                sh 'sbt dist'
                sh 'docker build nationalarchives/tdr-transfer-frontend .'
                withCredentials([usernamePassword(credentialsId: 'docker', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    sh 'echo $PASSWORD | docker login --username $USERNAME --password-stdin'
                }
            }
        }
  }
}
