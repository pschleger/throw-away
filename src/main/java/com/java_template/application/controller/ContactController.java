package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.contact.version_1.Contact;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for managing contacts in the address book application,
 * providing CRUD operations and search functionality for personal contacts.
 */
@RestController
@RequestMapping("/ui/contact")
@CrossOrigin(origins = "*")
public class ContactController {

    private static final Logger logger = LoggerFactory.getLogger(ContactController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ContactController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new contact
     * POST /ui/contact
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Contact>> createContact(@Valid @RequestBody Contact contact) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Contact.ENTITY_NAME).withVersion(Contact.ENTITY_VERSION);
            EntityWithMetadata<Contact> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, contact.getContactId(), "contactId", Contact.class);

            if (existing != null) {
                logger.warn("Contact with business ID {} already exists", contact.getContactId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Contact already exists with ID: %s", contact.getContactId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Contact> response = entityService.create(contact);
            logger.info("Contact created with ID: {}", response.metadata().getId());

            // Build Location header for the created resource
            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create contact: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get contact by technical UUID
     * GET /ui/contact/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Contact>> getContactById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Contact.ENTITY_NAME).withVersion(Contact.ENTITY_VERSION);
            EntityWithMetadata<Contact> response = entityService.getById(id, modelSpec, Contact.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve contact with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get contact by business identifier
     * GET /ui/contact/business/{contactId}
     */
    @GetMapping("/business/{contactId}")
    public ResponseEntity<EntityWithMetadata<Contact>> getContactByBusinessId(@PathVariable String contactId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Contact.ENTITY_NAME).withVersion(Contact.ENTITY_VERSION);
            EntityWithMetadata<Contact> response = entityService.findByBusinessId(
                    modelSpec, contactId, "contactId", Contact.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve contact with business ID '%s': %s", contactId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update contact with optional workflow transition
     * PUT /ui/contact/{id}?transition=update_contact
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Contact>> updateContact(
            @PathVariable UUID id,
            @Valid @RequestBody Contact contact,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Contact> response = entityService.update(id, contact, transition);
            logger.info("Contact updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update contact with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all contacts with pagination and optional filtering
     * GET /ui/contact?page=0&size=20&state=active
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Contact>>> listContacts(
            Pageable pageable,
            @RequestParam(required = false) String state) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Contact.ENTITY_NAME).withVersion(Contact.ENTITY_VERSION);

            if (state == null || state.trim().isEmpty()) {
                // Use paginated findAll when no state filter
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Contact.class));
            } else {
                // For state filtering, get all results then manually paginate
                List<EntityWithMetadata<Contact>> contacts = entityService.findAll(modelSpec, Contact.class);

                // Filter by state (state is in metadata, not entity)
                contacts = contacts.stream()
                        .filter(contact -> state.equals(contact.metadata().getState()))
                        .toList();

                // Manually paginate the filtered results
                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), contacts.size());
                List<EntityWithMetadata<Contact>> pageContent = start < contacts.size()
                    ? contacts.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<Contact>> page = new PageImpl<>(pageContent, pageable, contacts.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list contacts: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Search contacts by name
     * GET /ui/contact/search?name=john
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Contact>>> searchContactsByName(@RequestParam String name) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Contact.ENTITY_NAME).withVersion(Contact.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();
            
            // Search in both first name and last name
            SimpleCondition firstNameCondition = new SimpleCondition()
                    .withJsonPath("$.firstName")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(name));
            conditions.add(firstNameCondition);

            SimpleCondition lastNameCondition = new SimpleCondition()
                    .withJsonPath("$.lastName")
                    .withOperation(Operation.CONTAINS)
                    .withValue(objectMapper.valueToTree(name));
            conditions.add(lastNameCondition);

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.OR)
                    .withConditions(conditions);

            List<EntityWithMetadata<Contact>> contacts = entityService.search(modelSpec, condition, Contact.class);
            return ResponseEntity.ok(contacts);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search contacts by name '%s': %s", name, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Archive contact (soft delete)
     * POST /ui/contact/{id}/archive
     */
    @PostMapping("/{id}/archive")
    public ResponseEntity<EntityWithMetadata<Contact>> archiveContact(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Contact.ENTITY_NAME).withVersion(Contact.ENTITY_VERSION);
            EntityWithMetadata<Contact> current = entityService.getById(id, modelSpec, Contact.class);

            EntityWithMetadata<Contact> response = entityService.update(id, current.entity(), "archive_contact");
            logger.info("Contact archived with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to archive contact with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Reactivate archived contact
     * POST /ui/contact/{id}/reactivate
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Contact>> reactivateContact(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Contact.ENTITY_NAME).withVersion(Contact.ENTITY_VERSION);
            EntityWithMetadata<Contact> current = entityService.getById(id, modelSpec, Contact.class);

            EntityWithMetadata<Contact> response = entityService.update(id, current.entity(), "reactivate_contact");
            logger.info("Contact reactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to reactivate contact with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete contact by technical UUID (hard delete)
     * DELETE /ui/contact/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Contact deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete contact with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}
