package com.hedera.agentplatform.payments.service;

import com.hedera.agentplatform.payments.dto.CreatePaymentRequest;
import com.hedera.agentplatform.payments.entity.ContactEntity;
import com.hedera.agentplatform.payments.repository.ContactRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Names someone can be paid by. A name resolves to exactly one account, or to nothing. */
@Service
public class ContactService {

  private static final String NAME = "[\\p{L}][\\p{L}\\p{N} .'-]{0,39}";

  private final ContactRepository contacts;

  public ContactService(ContactRepository contacts) {
    this.contacts = contacts;
  }

  public List<ContactEntity> list() {
    return contacts.findAllByOrderByNameAsc();
  }

  public ContactEntity add(String name, String accountId) {
    String clean = name == null ? "" : name.trim();
    if (!clean.matches(NAME)) {
      throw new IllegalArgumentException(
          "A contact name starts with a letter and has at most 40 letters, digits or spaces");
    }
    if (accountId == null || !accountId.trim().matches(CreatePaymentRequest.ACCOUNT_ID)) {
      throw new IllegalArgumentException("must be a Hedera account id like 0.0.12345");
    }
    if (contacts.findByNameKey(key(clean)).isPresent()) {
      throw new IllegalStateException("A contact named " + clean + " already exists");
    }
    ContactEntity contact = new ContactEntity();
    contact.id = "contact_" + UUID.randomUUID();
    contact.name = clean;
    contact.nameKey = key(clean);
    contact.accountId = accountId.trim();
    contact.createdAt = Instant.now();
    return contacts.save(contact);
  }

  public void delete(String id) {
    if (!contacts.existsById(id)) {
      throw new IllegalArgumentException("Unknown contact: " + id);
    }
    contacts.deleteById(id);
  }

  public Optional<ContactEntity> byName(String name) {
    return name == null ? Optional.empty() : contacts.findByNameKey(key(name.trim()));
  }

  private static String key(String name) {
    return name.toLowerCase(Locale.ROOT);
  }
}
