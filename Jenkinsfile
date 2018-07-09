pipeline {
    agent {
      label "jenkins-maven"
    }
    environment {
      ORG               = 'mraible'
      APP_NAME          = 'jx-demo'
      CHARTMUSEUM_CREDS = credentials('jenkins-x-chartmuseum')
      OKTA_CLIENT_TOKEN = credentials('OKTA_CLIENT_TOKEN')
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
          sh """echo "##### Add Google Chrome's repo to sources..."
                echo "deb http://dl.google.com/linux/chrome/deb/ stable main" | sudo tee -a /etc/apt/sources.list
                # Install Google's public key used for signing packages (e.g. Chrome)
                wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | sudo apt-key add -
                # Update apt sources
                sudo apt-get update

                echo "##### Installing Headless Chrome dependencies..."
                sudo apt-get install -y libxpm4 libxrender1 libgtk2.0-0 libnss3 libgconf-2-4
                sudo apt-get install -y google-chrome-stable
                sudo apt-get install -y xvfb gtk2-engines-pixbuf
                sudo apt-get install -y xfonts-cyrillic xfonts-100dpi xfonts-75dpi xfonts-base xfonts-scalable
                sudo apt-get install -y imagemagick x11-apps

                ## Since https://wiki.jenkins-ci.org/display/JENKINS/ChromeDriver+plugin doesn't work...
                echo "##### Downloading latest ChromeDriver..."
                LATEST=$(wget -q -O - http://chromedriver.storage.googleapis.com/LATEST_RELEASE)
                sudo wget http://chromedriver.storage.googleapis.com/$LATEST/chromedriver_linux64.zip
                echo "##### Extracting and symlinking chromedriver to PATH so it's available globally"
                sudo unzip chromedriver_linux64.zip && sudo ln -s $PWD/chromedriver /usr/local/bin/chromedriver"""

            dir ('./holdings-api') {
              sh "mvn versions:set -DnewVersion=$PREVIEW_VERSION"
              sh "Xvfb :99 &"
              sh "DISPLAY=:99 mvn install -Pprod,e2e"
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
