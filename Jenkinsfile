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
  }
}
