package com.java_template.application.processor;

import com.java_template.application.entity.contact.version_1.Contact;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ABOUTME: Processor for updating contact information in the address book,
 * handling business logic for contact updates and timestamp management.
 */
@Component
public class UpdateContactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateContactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UpdateContactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Contact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid contact entity wrapper")
                .map(this::processContactUpdate)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for Contact
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Contact> entityWithMetadata) {
        Contact contact = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return contact != null && contact.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic for updating contact information
     */
    private EntityWithMetadata<Contact> processContactUpdate(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Contact> context) {

        EntityWithMetadata<Contact> entityWithMetadata = context.entityResponse();
        Contact contact = entityWithMetadata.entity();

        logger.debug("Updating contact: {}", contact.getContactId());

        // Update the timestamp to track when the contact was last modified
        contact.setUpdatedAt(LocalDateTime.now());

        // Validate contact information
        validateContactData(contact);

        logger.info("Contact {} updated successfully", contact.getContactId());

        return entityWithMetadata;
    }

    /**
     * Validates contact data for business rules
     */
    private void validateContactData(Contact contact) {
        // Ensure email format is valid if provided
        if (contact.getEmail() != null && !contact.getEmail().trim().isEmpty()) {
            if (!isValidEmail(contact.getEmail())) {
                logger.warn("Invalid email format for contact: {}", contact.getContactId());
            }
        }

        // Ensure phone format is valid if provided
        if (contact.getPhone() != null && !contact.getPhone().trim().isEmpty()) {
            if (!isValidPhone(contact.getPhone())) {
                logger.warn("Invalid phone format for contact: {}", contact.getContactId());
            }
        }
    }

    /**
     * Simple email validation
     */
    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    /**
     * Simple phone validation
     */
    private boolean isValidPhone(String phone) {
        // Remove common formatting characters
        String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)\\+]", "");
        return cleanPhone.matches("\\d{10,15}");
    }
}
