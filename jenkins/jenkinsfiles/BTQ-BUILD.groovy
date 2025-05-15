pipeline{
  agent {
    kubernetes {
      yaml readTrusted('jenkins/pod-templates/BTQ_BUILD_shell_kaniko_pod.yaml')
      defaultContainer "shell"
    }
  }

  options {
    buildDiscarder logRotator(numToKeepStr: '100')
  }
  parameters {
    string(name: 'TAG', defaultValue: '1.2.2', description: 'latest tag')
    string(name: 'BRANCH', defaultValue: 'main', description: 'default branch')
    choice(name: 'SERVICE', choices: ["adservice","cartservice","checkoutservice", "currencyservice","emailservice","frontend","paymentservice","productcatalogservice","recommendationservice","shippingservice","sealightsservice"], description: 'Service name to build')
    string(name: 'BUILD_NAME', defaultValue: 'none', description: 'build name')
  }
  environment{
    ECR_FULL_NAME = "btq-${params.SERVICE}"
    ECR_URI = "474620256508.dkr.ecr.eu-west-1.amazonaws.com/${env.ECR_FULL_NAME}"
    SL_TOKEN = (sh(returnStdout: true, script:"aws secretsmanager get-secret-value --region eu-west-1 --secret-id 'btq/template_token' | jq -r '.SecretString' | jq -r '.template_token'" )).trim()
    TAG = "template_${params.TAG}"
  }

  stages{
    stage('Init') {
      steps {
        script {
          // Clone the repository with the specified branch.
          git branch: params.BRANCH, url: 'https://github.com/Sealights-btq/template-btq.git'
          currentBuild.displayName = "${SERVICE}-${BUILD_NAME}"
          stage("Build Docker ${params.SERVICE} Image") {
            container(name: 'kaniko'){
              script {
                def CONTEXT = params.SERVICE == "cartservice" ? "./src/${params.SERVICE}/src" : "./src/${params.SERVICE}"
                def DP = "${CONTEXT}/Dockerfile"
                def D = "${env.ECR_URI}:${env.TAG}"
                def BRANCH = params.BRANCH
                def BUILD_NAME = "${params.BUILD_NAME}"
                def SL_TOKEN = env.SL_TOKEN
                def AGENT_URL = params.AGENT_URL
                def AGENT_URL_SLCI = params.AGENT_URL_SLCI

                sh """
                    /kaniko/executor \
                    --context ${CONTEXT} \
                    --dockerfile ${DP} \
                    --destination ${D} \
                    --build-arg BRANCH=${BRANCH} \
                    --build-arg BUILD_NAME=${BUILD_NAME} \
                    --build-arg SEALIGHTS_TOKEN=${SL_TOKEN}
                """
              }
            }
          }
        }
      }
    }
  }
}
