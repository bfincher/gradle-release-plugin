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

def call(Map config = [:]) {

    paramsList = []

    // You can also set the default value using the 'defaultValue' option
    paramsList << extendedChoice(name: 'Perform Release', description: 'Select the type of release or leave blank for now release', 
        type: 'PT_RADIO', 
        value: 'Major,Minor,Patch', visibleItemCount: 5)

    properties([parameters(paramsList)])
}
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
