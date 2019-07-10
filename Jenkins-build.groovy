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
                        app = docker.build('${dockerImage}:${dockerTag}')
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
                stage('Deploy'){

                    sh 'sed -i -e "s/DOCK_IMAGE_TAG/${dockerTag}/g"  deployment.json'
                    sh 'sed -i -e "s/POD_NAME/${podName}/g"  deployment.json'
                    sh 'sed -i -e "s/CLUSTER_VALUE/$deployment/g" deployment.json'
                    sh 'sed -i -e "s/SFS_HOSTNAME/$fileserver/g" deployment.json'
                    sh 'sed -i -e "s/NODE_POOL/$nodePool/g"  deployment.json'
                    sh 'sed -i -e "s/MEMORY_REQUESTS/$memory/g"  deployment.json'
                    sh 'sed -i -e "s/CPU_REQUESTS/$cpu/g"  deployment.json'
                    sh 'sed -i -e "s/POD_SCALE/$scale/g"  deployment.json'
                    sh 'sed -i -e "s/GPU_LIMIT/$gpu/g"  deployment.json'

                    container('kubectl') {
                        sh 'kubectl config get-contexts'
                    }

                    if("$deployment" == "Test"){
                        sh 'echo "test deployment start"'

                        if("$mode" == "New"){
                            sh 'echo "new deployment"'

                            container('kubectl') {
                                sh 'kubectl delete --context gke_paparazme-158910_us-central1-a_ppzme-test-cluster deployments/${podName} || exit 0'
                                sh 'kubectl create --context gke_paparazme-158910_us-central1-a_ppzme-test-cluster -f deployment.json'
                            }
                        }
                        else {
                            sh 'echo "set new image"'

                            container('kubectl') {
                                sh 'kubectl set image --context gke_paparazme-158910_us-central1-a_ppzme-test-cluster deployments/${podName} ${podName}=gcr.io/paparazme-158910/${podName}:${dockerTag} || exit 0'
                            }
                        }
                    }
                    if("$deployment" == "Prod"){
                        sh 'echo "production deployment"'

                        if("$mode" == "New"){
                            container('kubectl') {
                                sh 'kubectl delete --context gke_paparazme-158910_us-central1-a_ppzme-prod deployments/${podName} || exit 0'
                                sh 'kubectl create --context gke_paparazme-158910_us-central1-a_ppzme-prod -f deployment.json'
                            }
                        }
                        else {
                            container('kubectl') {
                                sh 'kubectl set image --context gke_paparazme-158910_us-central1-a_ppzme-prod deployments/${podName} ${podName}=gcr.io/paparazme-158910/${podName}:${dockerTag} || exit 0'
                            }
                        }
                    }
                    if("$deployment" == "None"){
                        sh 'echo "none deployment"'

                        sh ('ls .')
                        sh 'echo "Deploy skipped"'
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
