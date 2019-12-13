pipeline {
  agent none

  stages {
    stage('Test') {
        agent {
            ecs {
                inheritFrom 'sbt'
            }
        }
        steps {
            checkout scm
            sh 'sbt test'
        }
    }
  }
}
