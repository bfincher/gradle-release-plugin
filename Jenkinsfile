def performRelease = false
def gradleOpts = "-s"
gradleOpts += " --build-cache"
gradleOpts += " -PlocalNexus=https://nexus.fincherhome.com/nexus/content/groups/public"

properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')), 
disableConcurrentBuilds(), pipelineTriggers([[$class: 'PeriodicFolderTrigger', interval: '1d']])])

pipeline {
  agent any

  parameters {
      string(defaultValue: '', description: 'Extra Gradle Options', name: 'extraGradleOpts')
      booleanParam(name: 'majorRelease', defaultValue: false, description: 'Perform a major release')
      booleanParam(name: 'minorRelease', defaultValue: false, description: 'Perform a minor release')
      booleanParam(name: 'patchRelease', defaultValue: false, description: 'Perform a patch release')
  }

  tools {
    jdk 'jdk11'
  }

  stages {
    stage('PrepareBuild') {
      steps {
        script {
          def releaseOptionCount = 0;
          def prepareReleaseOptions = "";
          
          if (params.majorRelease) {
            performRelease = true
            prepareReleaseOptions = "--releaseType MAJOR"
            releaseOptionCount++
          }
          if (params.minorRelease) {
            performRelease = true
            prepareReleaseOptions = "--releaseType MINOR"
            releaseOptionCount++
          }
          if (params.patchRelease) {
            performRelease = true
            prepareReleaseOptions = "--releaseType PATCH"
            releaseOptionCount++
          }

          if (releaseOptionCount > 1) {
            error("Only one of major, minor, or patch release options can be selected")
          }

          if (!params.extraGradleOpts.isEmpty()) {
            gradleOpts = gradleOpts + extraGradleOpts
          }
          
          if (performRelease) {
            sh './gradlew prepareRelease ' + $prepareReleaseOptions + ' ' + gradleOpts 
          }
        }
      }
    }

    stage('Build') {
      steps {
        sh './gradlew clean build ' + gradleOpts
      }
    }
    
    stage('Finalize') {
        steps {
          script {
            if (performRelease) {
			  sh './gradlew finalizeRelease ' + gradleOpts
            }
          }
        }
    }
  }
}
