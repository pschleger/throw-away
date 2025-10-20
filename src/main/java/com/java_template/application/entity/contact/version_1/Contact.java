package com.java_template.application.entity.contact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Contact entity for address book application that stores personal
 * contact information including name, email, phone, and address details.
 */
@Data
public class Contact implements CyodaEntity {
    public static final String ENTITY_NAME = Contact.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String contactId;
    
    // Required core contact fields
    private String firstName;
    private String lastName;
    
    // Optional contact fields
    private String email;
    private String phone;
    private Address address;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(org.cyoda.cloud.api.event.common.EntityMetadata metadata) {
        // Validate required fields
        return contactId != null && !contactId.trim().isEmpty() &&
               firstName != null && !firstName.trim().isEmpty() &&
               lastName != null && !lastName.trim().isEmpty();
    }

    /**
     * Nested class for address information
     * Contains all address-related fields for the contact
     */
    @Data
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String country;
    }
}
