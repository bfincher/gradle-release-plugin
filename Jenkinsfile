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
        def releaseOptionCount = 0;
        if (!params.majorRelease.isEmpty()) {
          performRelease = true
          releaseOptionCount++
        }
        if (!params.minorRelease.isEmpty()) {
          performRelease = true
          releaseOptionCount++
        }
        if (!params.patchRelease.isEmpty()) {
          performRelease = true
          releaseOptionCount++
        }

        if (releaseOptionCount > 0) {
          error("Only one of major, minor, or patch release options can be selected"
        }

        if (!params.extraGradleOpts.isEmpty()) {
          gradleOpts = gradleOpts + extraGradleOpts
        }


      }
    }

    stage('Build') {
      steps {
        sh './gradlew clean build ' + gradleOpts
      }
    }
  }
}
