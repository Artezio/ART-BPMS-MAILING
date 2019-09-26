package com.artezio.bpm.camunda.mailing;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.mail.Message.RecipientType.*;

@Named
public class MailSender implements JavaDelegate {

    private final static String SMTP_HOST = System.getProperty("SMTP_HOST", "localhost");
    private final static String SMTP_PORT = System.getProperty("SMTP_PORT", "25000");
    private final static String SMTP_USERNAME = System.getProperty("SMTP_USERNAME", "");
    private final static String SMTP_PASSWORD = System.getProperty("SMTP_PASSWORD", "");
    private static final String STARTTLS = System.getProperty("STARTTLS", "false");

    @Inject
    private MailTemplateManager mailTemplateManager;
    @Resource(mappedName = "java:global/camunda-bpm-platform/mail/com.artezio.bpm.camunda")
    private Session session;
    private Tika tika = new Tika();

    @PostConstruct
    public void initMailSession() {
        if (!StringUtils.isEmpty(SMTP_PASSWORD)) {
            session.getProperties().put("mail.smtp.auth", "true");
            session.getProperties().put("mail.smtp.user", SMTP_USERNAME);
        } else {
            session.getProperties().put("mail.smtp.auth", "false");
        }
        session.getProperties().put("mail.smtp.host", SMTP_HOST);
        session.getProperties().put("mail.smtp.port", SMTP_PORT);
        session.getProperties().put("mail.smtp.starttls.enable", STARTTLS);
    }

    public void execute(DelegateExecution execution) throws Exception {
        MimeMessage mimeMessage = createMimeMessage(execution);
        Transport.send(mimeMessage, SMTP_USERNAME, SMTP_PASSWORD);
    }

    private MimeMessage createMimeMessage(DelegateExecution execution) throws MessagingException {
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setSentDate(new Date());
        addSenderAndRecipients(mimeMessage, execution);
        addContent(mimeMessage, execution);
        return mimeMessage;
    }

    private void addSenderAndRecipients(MimeMessage mimeMessage, DelegateExecution execution) throws MessagingException {
        String sender = (String) execution.getVariable("mailSender");
        String recipients = (String) execution.getVariable("mailRecipients");
        Optional<String> ccRecipients = Optional.ofNullable((String) execution.getVariable("mailCcRecipients"));
        Optional<String> bccRecipients = Optional.ofNullable((String) execution.getVariable("mailBccRecipients"));

        mimeMessage.setFrom(new InternetAddress(sender));
        addRecipient(mimeMessage, TO, recipients);
        ccRecipients.ifPresent(value -> addRecipient(mimeMessage, CC, value));
        bccRecipients.ifPresent(value -> addRecipient(mimeMessage, BCC, value));
    }

    private void addRecipient(MimeMessage mimeMessage, Message.RecipientType recipientType, String recipients) {
        try {
            mimeMessage.addRecipients(recipientType, InternetAddress.parse(recipients, false));
        } catch (MessagingException e) {
            throw new RuntimeException("Error while forming mail recipient list.", e);
        }
    }

    private void addContent(MimeMessage mimeMessage, DelegateExecution execution) throws MessagingException {
        String mailTemplate = (String) execution.getVariable("mailTemplate");
        Map<String, Object> mailProperties = execution.getVariables();

        String mailSubjectText = mailTemplateManager.getTemplateText(execution, mailTemplate + "_subject.ftl", mailProperties);
        String mailBodyText = mailTemplateManager.getTemplateText(execution, mailTemplate + "_body.ftl", mailProperties);

        mimeMessage.setSubject(mailSubjectText, UTF_8.name());
        MimeMultipart messageMultipartData = new MimeMultipart("related");

        BodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setContent(mailBodyText, "text/html");
        messageMultipartData.addBodyPart(messageBodyPart);

        Optional<List<String>> mailImageNames = Optional.ofNullable((List<String>) mailProperties.get("mailImageNames"));
        mailImageNames.ifPresent(value ->
                value.forEach(mailImageName -> addImage(mailImageName, messageMultipartData, execution)));

        mimeMessage.setContent(messageMultipartData);
    }

    private void addImage(String mailImageName, MimeMultipart multipart, DelegateExecution execution) {
        BodyPart messageImagePart = new MimeBodyPart();
        try(InputStream mailImage = mailTemplateManager.getMailImage(execution, mailImageName)) {
            String mailImageType = tika.detect(mailImage);
            DataSource fds = new ByteArrayDataSource(mailImage, mailImageType);
            messageImagePart.setDataHandler(new DataHandler(fds));
            messageImagePart.setHeader("Content-ID", "<" + mailImageName + ">");
            multipart.addBodyPart(messageImagePart);
        } catch (MessagingException | IOException e) {
            throw new RuntimeException("Error while creating message content", e);
        }
    }

}
