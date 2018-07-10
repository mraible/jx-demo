pipeline {
    agent {
      label "jenkins-maven"
    }
    environment {
      ORG               = 'mraible'
      APP_NAME          = 'jx-demo'
      CHARTMUSEUM_CREDS = credentials('jenkins-x-chartmuseum')
      OKTA_CLIENT_TOKEN = credentials('OKTA_CLIENT_TOKEN')
      OKTA_APP_ID       = credentials('OKTA_APP_ID')
      E2E_USERNAME      = credentials('E2E_USERNAME')
      E2E_PASSWORD      = credentials('E2E_PASSWORD')
      CI                = true
    }
    stages {
      stage('CI Build and push snapshot') {
        when {
          branch 'PR-*'
        }
        environment {
          PREVIEW_VERSION = "0.0.0-SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER"
          PREVIEW_NAMESPACE = "$APP_NAME-$BRANCH_NAME".toLowerCase()
          HELM_RELEASE = "$PREVIEW_NAMESPACE".toLowerCase()
        }
        steps {
          container('maven') {
            dir ('./holdings-api') {
              sh "mvn versions:set -DnewVersion=$PREVIEW_VERSION"
              sh "mvn install -Pprod -DskipTests"
            }

            sh 'export VERSION=$PREVIEW_VERSION && skaffold build -f skaffold.yaml'
            sh "jx step post build --image $DOCKER_REGISTRY/$ORG/$APP_NAME:$PREVIEW_VERSION"
          }

          dir ('./charts/preview') {
            container('maven') {
              sh "make preview"
              sh "jx preview --app $APP_NAME --dir ../.."
            }
          }

          // Add redirect URI in Okta
          dir ('./holdings-api') {
            container('maven') {
              sh '''
              yum install -y jq
              previewURL=$(jx get preview -o json|jq  -r ".items[].spec | select (.previewGitInfo.name==\\"$CHANGE_ID\\") | .previewGitInfo.applicationURL")
              echo $previewURL > ../PREVIEW_URL
              mvn exec:java@add-redirect -DappId=$OKTA_APP_ID -DredirectUri=${previewURL}/login
              '''
            }
          }
        }
      }
      stage('Run e2e tests') {
        agent {
          label "jenkins-nodejs"
        }
        steps {
          container('nodejs') {
            sh "previewURL=\$(cat PREVIEW_URL)"
            sh "echo 'Running e2e tests on ${previewURL}...'"
            dir ('./crypto-pwa') {
              sh "npm install"
              sh "Xvfb :99 &"
              sh "DISPLAY=:99 npm run e2e -- --baseUrl=${previewURL}"
            }
          }
        }
      }
      stage('Build Release') {
        when {
          branch 'master'
        }
        steps {
          container('maven') {
            // ensure we're not on a detached head
            sh "git checkout master"
            sh "git config --global credential.helper store"

            sh "jx step git credentials"
            // so we can retrieve the version in later steps
            sh "echo \$(jx-release-version) > VERSION"
            dir ('./holdings-api') {
              sh "mvn versions:set -DnewVersion=\$(cat ../VERSION)"
            }
          }
          dir ('./charts/jx-demo') {
            container('maven') {
              sh "make tag"
            }
          }
          container('maven') {
            dir ('./holdings-api') {
              sh 'mvn clean deploy -Pprod'
            }

            sh 'export VERSION=`cat VERSION` && skaffold build -f skaffold.yaml'
            sh "jx step post build --image $DOCKER_REGISTRY/$ORG/$APP_NAME:\$(cat VERSION)"
          }
        }
      }
      stage('Promote to Environments') {
        when {
          branch 'master'
        }
        steps {
          dir ('./charts/jx-demo') {
            container('maven') {
              sh 'jx step changelog --version v\$(cat ../../VERSION)'

              // release the helm chart
              sh 'jx step helm release'

              // promote through all 'Auto' promotion Environments
              sh 'jx promote -b --all-auto --timeout 1h --version \$(cat ../../VERSION)'
            }
          }
        }
      }
    }
    post {
        always {
            cleanWs()
        }
        failure {
            input """Pipeline failed. 
We will keep the build pod around to help you diagnose any failures. 

Select Proceed or Abort to terminate the build pod"""
        }
    }
  }