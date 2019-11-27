package com.artezio.bpm.camunda.mailing;

import freemarker.cache.TemplateLoader;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BpmDeploymentTemplateLoader implements TemplateLoader {

    private static final Map<String, byte[]> TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private RepositoryService repositoryService;
    private DelegateExecution execution;

    public BpmDeploymentTemplateLoader(DelegateExecution execution, RepositoryService repositoryService) {
        this.execution = execution;
        this.repositoryService = repositoryService;
    }

    @Override
    public Object findTemplateSource(String templateName) throws IOException {
        String deploymentId = getDeploymentId();
        byte[] template = IOUtils.toByteArray(repositoryService.getResourceAsStream(deploymentId, templateName));
        return TEMPLATE_CACHE.computeIfAbsent(deploymentId + "." + templateName, templateCacheKey -> template);
    }

    @Override
    public long getLastModified(Object o) {
        return 0;
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) throws IOException {
        return new InputStreamReader(new ByteArrayInputStream((byte[]) templateSource), encoding);
    }

    @Override
    public void closeTemplateSource(Object templateSource) {
    }

    private String getDeploymentId() {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(execution.getProcessDefinitionId())
                .singleResult()
                .getDeploymentId();
    }

}
