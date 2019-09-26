package com.artezio.bpm.camunda.mailing;

import freemarker.cache.TemplateLoader;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BpmDeploymentTemplateLoader implements TemplateLoader {

    private static final Map<String, InputStream> TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private RepositoryService repositoryService;
    private DelegateExecution execution;

    public BpmDeploymentTemplateLoader(DelegateExecution execution, RepositoryService repositoryService) {
        this.execution = execution;
        this.repositoryService = repositoryService;
    }

    @Override
    public Object findTemplateSource(String templateName) throws IOException {
        String deploymentId = getDeploymentId();
        return TEMPLATE_CACHE.computeIfAbsent(deploymentId + "." + templateName, templateCacheKey ->
                repositoryService.getResourceAsStream(deploymentId, templateName));
    }

    @Override
    public long getLastModified(Object o) {
        return 0;
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) throws IOException {
        return new InputStreamReader((InputStream) templateSource, encoding);
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
        if (templateSource != null) {
            ((InputStream) templateSource).close();
        }
    }

    private String getDeploymentId() {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(execution.getProcessDefinitionId())
                .singleResult()
                .getDeploymentId();
    }

}
