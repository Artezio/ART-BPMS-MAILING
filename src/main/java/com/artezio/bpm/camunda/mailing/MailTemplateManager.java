package com.artezio.bpm.camunda.mailing;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.delegate.DelegateExecution;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

@Named
public class MailTemplateManager {

    @Inject
    private RepositoryService repositoryService;

    public String getTemplateText(DelegateExecution execution, String templateFileName, Map<String, Object> dataModel) {
        try {
            Template template = getConfiguration(execution).getTemplate(templateFileName);
            Writer output = new StringWriter();
            template.process(dataModel, output);
            return output.toString();
        } catch (TemplateException e) {
            throw new RuntimeException("Some problems occurred while executing the template!", e);
        } catch (IOException e) {
            throw new RuntimeException("I/O problem occurred with template file!", e);
        }
    }

    public InputStream getMailImage(DelegateExecution execution, String imageName) {
        String deploymentId = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(execution.getProcessDefinitionId())
                .singleResult()
                .getDeploymentId();
        return repositoryService.getResourceAsStream(deploymentId, imageName);
    }

    private Configuration getConfiguration(DelegateExecution execution) {
        Configuration configuration = new Configuration();
        configuration.setObjectWrapper(new DefaultObjectWrapper());
        configuration.setLocalizedLookup(false);
        configuration.setTemplateLoader(new BpmDeploymentTemplateLoader(execution, repositoryService));
        return configuration;
    }

}