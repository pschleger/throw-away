package com.java_template.application.criterion;

import com.java_template.application.entity.contact.version_1.Contact;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Criterion for validating contact data and business rules in the
 * address book application, ensuring data quality and consistency.
 */
@Component
public class ContactValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ContactValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Contact validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Contact.class, this::validateContact)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the Contact entity
     */
    private EvaluationOutcome validateContact(CriterionSerializer.CriterionEntityEvaluationContext<Contact> context) {
        Contact contact = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (contact == null) {
            logger.warn("Contact entity is null");
            return EvaluationOutcome.fail("Contact entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!contact.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("Contact entity is not valid: {}", contact.getContactId());
            return EvaluationOutcome.fail("Contact entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate email format if provided
        if (contact.getEmail() != null && !contact.getEmail().trim().isEmpty()) {
            if (!isValidEmail(contact.getEmail())) {
                logger.warn("Invalid email format for contact: {}", contact.getContactId());
                return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Validate phone format if provided
        if (contact.getPhone() != null && !contact.getPhone().trim().isEmpty()) {
            if (!isValidPhone(contact.getPhone())) {
                logger.warn("Invalid phone format for contact: {}", contact.getContactId());
                return EvaluationOutcome.fail("Invalid phone format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        // Validate address if provided
        if (contact.getAddress() != null) {
            if (!isValidAddress(contact.getAddress())) {
                logger.warn("Invalid address for contact: {}", contact.getContactId());
                return EvaluationOutcome.fail("Invalid address information", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        }

        logger.debug("Contact validation passed for: {}", contact.getContactId());
        return EvaluationOutcome.success();
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

    /**
     * Basic address validation
     */
    private boolean isValidAddress(Contact.Address address) {
        // At least city should be provided for a valid address
        return address.getCity() != null && !address.getCity().trim().isEmpty();
    }
}
