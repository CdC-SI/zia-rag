@Library('zas-pipelinelibrary') _

mavenPipelineTemplate {
    node='java-25'
    mvnArgs='-B clean verify'
    dockerConfig = [imageRoot: 'zas/copilot', imageName: 'rag-service']
    email = [recipients: '']
    triggerDevPromotion = [ repositoryName : 'copilot-ocp-promote', versionProperty: 'rag-service.image.version' ]
}
