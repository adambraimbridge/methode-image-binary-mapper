package com.ft.methodeimagebinarymapper.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ft.message.consumer.MessageListener;
import com.ft.messaging.standards.message.v1.Message;
import com.ft.messaging.standards.message.v1.SystemId;
import com.ft.methodeimagebinarymapper.exception.IngesterException;
import com.ft.methodeimagebinarymapper.model.EomFile;
import com.ft.methodeimagebinarymapper.validation.PublishingValidator;
import com.ft.methodeimagebinarymapper.validation.UuidValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Predicate;

public class NativeCmsPublicationEventsListener
    implements MessageListener {

  private static final Logger LOG = LoggerFactory.getLogger(NativeCmsPublicationEventsListener.class);

  private final Predicate<Message> filter;
  private final MessageProducingContentMapper mapper;
  private final ObjectMapper objectMapper;
  private final SystemId systemId;
  private final UuidValidator uuidValidator;
  private final PublishingValidator publishingValidator;

  public NativeCmsPublicationEventsListener(String systemCode, MessageProducingContentMapper mapper, ObjectMapper objectMapper,
                                            UuidValidator uuidValidator, PublishingValidator publishingValidator) {
    this.systemId = SystemId.systemIdFromCode(systemCode);
    this.filter = msg -> (systemId.equals(msg.getOriginSystemId()));
    this.mapper = mapper;
    this.objectMapper = objectMapper;
    this.uuidValidator = uuidValidator;
    this.publishingValidator = publishingValidator;
  }

  @Override
  public boolean onMessage(Message message, String transactionId) {
    if (filter.test(message)) {
      LOG.info("Process message");
      handleMessage(message, transactionId);
    } else {
      LOG.info("Skip message with origin id {}.", message.getOriginSystemId());
    }
    return true;
  }

  private void handleMessage(Message message, String transactionId) {
    try {
      EomFile methodeContent = objectMapper.reader(EomFile.class).readValue(message.getMessageBody());
      uuidValidator.validate(methodeContent.getUuid());
      if (publishingValidator.isValidImageForPublishing(methodeContent)) {
        LOG.info("Importing content [{}] of type [{}] .", methodeContent.getUuid(), methodeContent.getType());
        LOG.info("Event for {}.", methodeContent.getUuid());
        mapper.mapImageBinary(methodeContent, transactionId, message.getMessageTimestamp());
      } else if (publishingValidator.isValidPDFForPublishing(methodeContent)) {
        LOG.info("Importing content [{}] of type [{}] .", methodeContent.getUuid(), methodeContent.getType());
        LOG.info("Event for {}.", methodeContent.getUuid());
        mapper.mapPDFBinary(methodeContent, transactionId, message.getMessageTimestamp());
      } else {
        LOG.info("Skip message [{}] of type [{}]", methodeContent.getUuid(), methodeContent.getType());
      }
    } catch (IOException e) {
      throw new IngesterException("Unable to parse Methode content message", e);
    }
  }

}
