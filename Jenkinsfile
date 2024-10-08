def performRelease = false
def baseNexusUrl = "http://nexus3:8081"
def gradleOpts = "-s --build-cache -PlocalNexus=${baseNexusUrl}/repository/public"
def buildCacheDir = ""

pipeline {
  agent { label 'gradle-8.10-jdk17' }

  parameters {
    string(defaultValue: '', description: 'Extra Gradle Options', name: 'extraGradleOpts')
    booleanParam(name: 'majorRelease', defaultValue: false, description: 'Perform a major release')
    booleanParam(name: 'minorRelease', defaultValue: false, description: 'Perform a minor release')
    booleanParam(name: 'patchRelease', defaultValue: false, description: 'Perform a patch release')
    booleanParam(name: 'publish', defaultValue: false, description: 'Publish to nexus')
    string(name: 'baseBuildCacheDir', defaultValue: '/cache', description: 'Base build cache dir')
    string(name: 'buildCacheName', defaultValue: 'default', description: 'Build cache name')

  }

  stages {
    stage('Prepare') {
      steps {
        script {
          
          buildCacheDir = sh(
              script: "src/main/resources/getBuildCache ${params.baseBuildCacheDir} ${params.buildCacheName}",
              returnStdout: true).trim()

          gradleOpts = gradleOpts + " --gradle-user-home=" + buildCacheDir

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
            gradleOpts = "${gradleOpts} ${params.extraGradleOpts}"
          }

          sh "git config --global user.email 'brian@fincherhome.com' && git config --global user.name 'Brian Fincher'"
          
          if (performRelease) {
            sh 'gradle prepareRelease ' + prepareReleaseOptions + ' ' + gradleOpts 
          }
          
        }
      }
    }
		
    stage('Build') {
      steps {
        sh 'gradle clean build ' + gradleOpts     
      }
    }
    
    stage('Finalize') {
      when { expression { performRelease || params.publish } }
      steps {
        script {
          
          if (performRelease || params.publish ) {
            def publishParams = '-PpublishUsername=${publishUsername} -PpublishPassword=${publishPassword}'
            publishParams += " -PpublishSnapshotUrl=${baseNexusUrl}/repository/snapshots"
            publishParams += " -PpublishReleaseUrl=${baseNexusUrl}/repository/releases"
            withCredentials([usernamePassword(credentialsId: 'nexus.fincherhome.com', usernameVariable: 'publishUsername', passwordVariable: 'publishPassword')]) {
              sh "gradle publish  ${gradleOpts} ${publishParams}"
            }
          }

          if (performRelease) {
            withCredentials([sshUserPrivateKey(credentialsId: "bfincher_git_private_key", keyFileVariable: 'keyfile')]) {
			  sh 'gradle finalizeRelease -PsshKeyFile=${keyfile} ' + gradleOpts
            }
          }
          
        }
      }
    }
  }

  post {
    always {
      sh("src/main/resources/releaseBuildCache ${buildCacheDir}")
      archiveArtifacts artifacts: 'build/reports/sonarlint/**/*'
      junit 'build/test-results/**/*.xml'
    }
  }
}

