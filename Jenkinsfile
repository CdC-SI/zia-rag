@Library('phenix-pipeline-library') _

mavenDockerBuild{
    displayParameters=true
    jdk='jdk25(x64)'
    mvnArgs='-B clean verify -Pinternal'
    dockerConfig = [imageRoot: 'zas/copilot', imageName: 'rag-service', buildParams: [HTTP_PROXY: this.env.HTTP_PROXY_OCP, HTTPS_PROXY: this.env.HTTP_PROXY_OCP, no_proxy: this.env.NO_PROXY], options: ['--rm'],
                    stash : ['**/Dockerfile','**/target/**','**/delivery/**']
    ]
    email = [recipients: '']
    triggerDevPromotion = [ repositoryName : 'copilot-ocp-promote', versionProperty: 'rag-service.image.version' ]
}
