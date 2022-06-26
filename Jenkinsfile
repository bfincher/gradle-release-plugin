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
    }

    tools {
        jdk 'jdk11'
    }

    stages {
        stage('Build') {
            steps {
                sh './gradlew clean build ' + gradleOpts
            }
        }
    }
}
