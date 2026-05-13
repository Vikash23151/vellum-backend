package com.vellum.newsletter.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.newsletter.from-email}")
    private String fromEmail;

    @Value("${app.newsletter.from-name}")
    private String fromName;

    @Value("${app.newsletter.base-url}")
    private String baseUrl;

    // CONFIRMATION EMAIL
    /*
     * Sent when user first subscribes (PENDING status).
     * Contains: confirm link with token.
     */
    @Async
    public void sendConfirmationEmail(String toEmail,
                                      String subscriberName,
                                      String token) {
        try {
            String confirmationUrl = baseUrl +
                    "/confirm-subscription?token=" + token;

            Context ctx = new Context();
            ctx.setVariable("subscriberName", subscriberName);
            ctx.setVariable("confirmationUrl", confirmationUrl);

            String html = templateEngine.process(
                    "confirmation-email", ctx);

            sendHtmlEmail(
                    toEmail,
                    "Please confirm your Vellum subscription",
                    html);

            log.info("✉️ Confirmation email sent to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send confirmation email to {}: {}",
                    toEmail, e.getMessage());
        }
    }

    // WELCOME EMAIL
    /*
     * Sent after user confirms subscription (ACTIVE status).
     * Contains: welcome message, link to blog, unsubscribe link.
     */
    @Async
    public void sendWelcomeEmail(String toEmail,
                                 String subscriberName,
                                 String token) {
        try {
            String unsubscribeUrl = baseUrl +
                    "/unsubscribe?token=" + token;
            String blogUrl = baseUrl;

            Context ctx = new Context();
            ctx.setVariable("subscriberName", subscriberName);
            ctx.setVariable("unsubscribeUrl", unsubscribeUrl);
            ctx.setVariable("blogUrl", blogUrl);

            String html = templateEngine.process(
                    "welcome-email", ctx);

            sendHtmlEmail(toEmail, "Welcome to Vellum! ", html);

            log.info("Welcome email sent to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}",
                    toEmail, e.getMessage());
        }
    }

    // NEWSLETTER / CAMPAIGN EMAIL
    /*
     * Sent for newsletter campaigns and new post alerts.
     * Each email is personalized with:
     * - recipient's name
     * - unique unsubscribe link (per subscriber token)
     */
    @Async
    public void sendNewsletterEmail(String toEmail,
                                    String subscriberName,
                                    String token,
                                    String subject,
                                    String htmlContent) {
        try {
            String unsubscribeUrl = baseUrl +
                    "/unsubscribe?token=" + token;
            String blogUrl = baseUrl;

            Context ctx = new Context();
            ctx.setVariable("subscriberName", subscriberName);
            ctx.setVariable("subject", subject);
            ctx.setVariable("content", htmlContent);
            ctx.setVariable("unsubscribeUrl", unsubscribeUrl);
            ctx.setVariable("blogUrl", blogUrl);

            String html = templateEngine.process(
                    "newsletter-email", ctx);

            sendHtmlEmail(toEmail, subject, html);

        } catch (Exception e) {
            log.error("Failed to send newsletter to {}: {}",
                    toEmail, e.getMessage());
            // Don't rethrow — one failed email shouldn't stop campaign
        }
    }

    // NEW POST NOTIFICATION EMAIL
    /*
     * Auto-sent when a post is published.
     * Triggered by RabbitMQ event (post.published).
     * Uses same newsletter template with post-specific content.
     */
    @Async
    public void sendNewPostNotification(String toEmail,
                                        String subscriberName,
                                        String token,
                                        String postTitle,
                                        String postExcerpt,
                                        String postSlug) {
        try {
            String postUrl = baseUrl + "/blog/" + postSlug;
            String unsubscribeUrl = baseUrl +
                    "/unsubscribe?token=" + token;

            // Build HTML content for new post notification
            String htmlContent = String.format("""
                <h2 style="color: #2d3748;">%s</h2>
                <p style="color: #4a5568; line-height: 1.7;">%s</p>
                <p>
                    <a href="%s"
                       style="background: #2d3748; color: #fff;
                              padding: 12px 28px; border-radius: 6px;
                              text-decoration: none; font-weight: 600;">
                        Read Full Article →
                    </a>
                </p>
                """,
                    postTitle,
                    postExcerpt != null ? postExcerpt : "",
                    postUrl);

            String subject = "New post on Vellum: " + postTitle;
            String blogUrl = baseUrl;

            Context ctx = new Context();
            ctx.setVariable("subscriberName", subscriberName);
            ctx.setVariable("subject", subject);
            ctx.setVariable("content", htmlContent);
            ctx.setVariable("unsubscribeUrl", unsubscribeUrl);
            ctx.setVariable("blogUrl", blogUrl);

            String html = templateEngine.process(
                    "newsletter-email", ctx);

            sendHtmlEmail(toEmail, subject, html);
            log.debug("✉️ New post notification sent to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send new post notification to {}: {}",
                    toEmail, e.getMessage());
        }
    }

    // PRIVATE HELPER
    /*
     * Core email sending method.
     * MimeMessage: supports HTML content (plain Message doesn't).
     * MimeMessageHelper: simplifies building MimeMessage.
     * setFrom(email, name): shows "Vellum Blog <noreply@vellum.com>"
     * setText(html, true): true = HTML mode (not plain text)
     */
    private void sendHtmlEmail(String to,
                               String subject,
                               String htmlContent)
            throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                message, true, "UTF-8");

        try {
            helper.setFrom(fromEmail, fromName);
        } catch (Exception e) {
            helper.setFrom(fromEmail);
        }

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }
}