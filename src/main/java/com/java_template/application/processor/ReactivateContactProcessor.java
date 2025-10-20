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
 * ABOUTME: Processor for reactivating archived contacts in the address book,
 * handling business logic for restoring contacts to active state.
 */
@Component
public class ReactivateContactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReactivateContactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ReactivateContactProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Contact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid contact entity wrapper")
                .map(this::processContactReactivation)
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
     * Main business logic for reactivating contact
     */
    private EntityWithMetadata<Contact> processContactReactivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Contact> context) {

        EntityWithMetadata<Contact> entityWithMetadata = context.entityResponse();
        Contact contact = entityWithMetadata.entity();

        logger.debug("Reactivating contact: {}", contact.getContactId());

        // Update the timestamp to track when the contact was reactivated
        contact.setUpdatedAt(LocalDateTime.now());

        // Add reactivation note
        if (contact.getNotes() == null || contact.getNotes().trim().isEmpty()) {
            contact.setNotes("Contact reactivated on " + LocalDateTime.now());
        } else {
            contact.setNotes(contact.getNotes() + " | Reactivated on " + LocalDateTime.now());
        }

        logger.info("Contact {} reactivated successfully", contact.getContactId());

        return entityWithMetadata;
    }
}
