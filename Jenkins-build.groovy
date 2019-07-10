podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'docker', image: 'docker', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'kubectl', image: 'gcr.io/cloud-builders/kubectl', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'gcloud', image: 'gcr.io/cloud-builders/gcloud', command: 'cat', ttyEnabled: true)
],
        volumes: [
                hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
        ]) {
    node ('mypod') {
        try{
            def app
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'github-user-credentials', usernameVariable: 'GITHUB_ACCESS_USR', passwordVariable: 'GITHUB_ACCESS_PSW']]) {
                stage('Checkout Code') {
                    def githubUrl = "https://${GITHUB_ACCESS_USR}:${GITHUB_ACCESS_PSW}@github.com/PomVom/${githubRepository}"
                    git branch: '${githubBranch}', credentialsId: 'github-user-credentials', url: githubUrl
                }
                stage('Build Image') {
                    container('docker') {
                        sh 'docker version'
                        docker.withRegistry("https://mirror.gcr.io") { 
                            app = docker.build('${dockerImage}:${dockerTag}')
                        }
                    }
                }
                stage('Test Image'){
                    sh 'echo "Test skipped"'
                }
                stage('push image'){
                    sh 'echo "push image"'
                    container('gcloud'){
                        sh 'gcloud docker --authorize-only'
                    }
                    container('docker')  {
                        sh 'docker push "${dockerImage}:${dockerTag}"'
                    }
                }
                stage('Provide cluster credentials'){
                    container('gcloud') {
                        if("$deployment" == "Prod"){
                            sh 'gcloud container clusters get-credentials ppzme-prod --zone us-central1-a --project paparazme-158910'
                        }
                        else{
                            sh 'gcloud container clusters get-credentials ppzme-test-cluster --zone us-central1-a --project paparazme-158910'
                        }
                    }
                }
            }
        }catch (err) {
            currentBuild.result = "FAILURE"
            sh 'echo "Send e-mail build failure"'
            throw err
        }finally {
            if(currentBuild.previousBuild != null && currentBuild.previousBuild.result == 'FAILURE' && currentBuild.currentResult == 'SUCCESS'){
                sh 'echo "Send e-mail build ok after failure"'
            }
            if (currentBuild.result == 'UNSTABLE') {
                sh 'echo "Send e-mail build unstable"'
            }
        }
    }
}
