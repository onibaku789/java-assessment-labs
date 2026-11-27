# notification-worker responsibility review

[All modules and counting conventions](README.md)

11 Java files. 14 types, 26 methods and 1 explicit constructors. 4 types and 1 members have multiple reasons to change.

## NotificationApp

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class NotificationApp`](../../notification-worker/src/main/java/study/locker/notification/NotificationApp.java#L6) | 1 | Spring Boot startup of the notification worker. |
| [`main(String[])`](../../notification-worker/src/main/java/study/locker/notification/NotificationApp.java#L8) | 1 | Spring Boot startup of the notification worker. |

## PickupMailListener

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class PickupMailListener`](../../notification-worker/src/main/java/study/locker/notification/adapter/kafka/PickupMailListener.java#L14) | 1 | Validated pickup-mail record binding and forwarding Kafka delivery attempts. |
| [`PickupMailListener(DeliveryHandler<PickupNotification>)`](../../notification-worker/src/main/java/study/locker/notification/adapter/kafka/PickupMailListener.java#L19) | 1 | Validated pickup-mail record binding and forwarding Kafka delivery attempts. |
| [`receive(PickupMailMessage, int)`](../../notification-worker/src/main/java/study/locker/notification/adapter/kafka/PickupMailListener.java#L23) | 1 | Validated pickup-mail record binding and forwarding Kafka delivery attempts. |

## PickupMailMessage

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record PickupMailMessage`](../../notification-worker/src/main/java/study/locker/notification/adapter/kafka/PickupMailMessage.java#L9) | 1 | Versioned mail-event payload validation and conversion to a pickup notification. |
| [`toNotification()`](../../notification-worker/src/main/java/study/locker/notification/adapter/kafka/PickupMailMessage.java#L16) | 1 | Versioned mail-event payload validation and conversion to a pickup notification. |

## SmtpPickupMail

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record SmtpPickupMail`](../../notification-worker/src/main/java/study/locker/notification/adapter/smtp/SmtpPickupMail.java#L16) | 2 | Mailpit/SMTP message contents, envelope addresses and stable message identity · Permanent recipient errors and transient mail infrastructure failures. |
| [`send(PickupNotification, String)`](../../notification-worker/src/main/java/study/locker/notification/adapter/smtp/SmtpPickupMail.java#L18) | 2 | Mailpit/SMTP message contents, envelope addresses and stable message identity · Permanent recipient errors and transient mail infrastructure failures. Builds a MIME message and maps sender or recipient failures to recovery outcomes. **Suggested change.** Classify a failed SMTP delivery separately from composing and sending the message. |
| [`permanentRecipient(Throwable)`](../../notification-worker/src/main/java/study/locker/notification/adapter/smtp/SmtpPickupMail.java#L39) | 1 | Permanent recipient errors and transient mail infrastructure failures. Distinguishes terminal address rejections from recoverable SMTP failures. |

**Refactor to consider.** Move recipient failure classification beside delivery recovery and keep MIME construction in the SMTP adapter.

## DeliverPickupNotification

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record DeliverPickupNotification`](../../notification-worker/src/main/java/study/locker/notification/application/DeliverPickupNotification.java#L7) | 1 | Sending a decrypted pickup code only while the notice is valid. |
| [`deliver(PickupNotification)`](../../notification-worker/src/main/java/study/locker/notification/application/DeliverPickupNotification.java#L21) | 1 | Sending a decrypted pickup code only while the notice is valid. Checks expiry before opening an event-bound envelope and sending mail. |

## DeliverPickupNotification.Decryptor

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface Decryptor`](../../notification-worker/src/main/java/study/locker/notification/application/DeliverPickupNotification.java#L8) | 1 | Worker-facing port for opening a code bound to its event. |
| [`open(String, String)`](../../notification-worker/src/main/java/study/locker/notification/application/DeliverPickupNotification.java#L9) | 1 | Worker-facing port for opening a code bound to its event. |

## DeliverPickupNotification.Mail

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface Mail`](../../notification-worker/src/main/java/study/locker/notification/application/DeliverPickupNotification.java#L12) | 1 | Worker-facing port for delivering a plaintext pickup code to a recipient. |
| [`send(PickupNotification, String)`](../../notification-worker/src/main/java/study/locker/notification/application/DeliverPickupNotification.java#L13) | 1 | Worker-facing port for delivering a plaintext pickup code to a recipient. |

## DeliverPickupNotification.Outcome

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`enum Outcome`](../../notification-worker/src/main/java/study/locker/notification/application/DeliverPickupNotification.java#L16) | 1 | Observable sent and expired delivery outcomes. |

## NotificationConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class NotificationConfiguration`](../../notification-worker/src/main/java/study/locker/notification/bootstrap/NotificationConfiguration.java#L17) | 2 | Clock, decryption and SMTP wiring for pickup delivery · Kafka correlation and outcome observation around delivery. |
| [`clock()`](../../notification-worker/src/main/java/study/locker/notification/bootstrap/NotificationConfiguration.java#L20) | 1 | Clock, decryption and SMTP wiring for pickup delivery. |
| [`delivery(Clock, JavaMailSender, String, String)`](../../notification-worker/src/main/java/study/locker/notification/bootstrap/NotificationConfiguration.java#L25) | 1 | Clock, decryption and SMTP wiring for pickup delivery. |
| [`handler(DeliveryEvents, DeliverPickupNotification)`](../../notification-worker/src/main/java/study/locker/notification/bootstrap/NotificationConfiguration.java#L37) | 1 | Kafka correlation and outcome observation around delivery. |

**Refactor to consider.** Bind the observed Kafka handler separately from the mail delivery use case.

## PickupNotification

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record PickupNotification`](../../notification-worker/src/main/java/study/locker/notification/domain/model/PickupNotification.java#L5) | 1 | Pickup notification data and the instant at which delivery expires. |
| [`expiredAt(Instant)`](../../notification-worker/src/main/java/study/locker/notification/domain/model/PickupNotification.java#L13) | 1 | Pickup notification data and the instant at which delivery expires. |

## SmtpPickupMailTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class SmtpPickupMailTest`](../../notification-worker/src/test/java/study/locker/notification/adapter/smtp/SmtpPickupMailTest.java#L21) | 1 | Treatment of temporary and permanent recipient rejections returned by SMTP. |
| [`temporaryMailboxRejectionLeavesTheDeliveryPending()`](../../notification-worker/src/test/java/study/locker/notification/adapter/smtp/SmtpPickupMailTest.java#L33) | 1 | Treatment of temporary and permanent recipient rejections returned by SMTP. |
| [`permanentMailboxRejectionIsEligibleForDeadLettering()`](../../notification-worker/src/test/java/study/locker/notification/adapter/smtp/SmtpPickupMailTest.java#L41) | 1 | Treatment of temporary and permanent recipient rejections returned by SMTP. |
| [`rejectRecipient(int)`](../../notification-worker/src/test/java/study/locker/notification/adapter/smtp/SmtpPickupMailTest.java#L47) | 1 | Treatment of temporary and permanent recipient rejections returned by SMTP. |

## DeliverPickupNotificationTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DeliverPickupNotificationTest`](../../notification-worker/src/test/java/study/locker/notification/application/DeliverPickupNotificationTest.java#L12) | 1 | Expiry and successful decryption-and-send behavior of the pickup notification use case. |
| [`expirySkipsBothDecryptionAndDelivery()`](../../notification-worker/src/test/java/study/locker/notification/application/DeliverPickupNotificationTest.java#L20) | 1 | Expiry and successful decryption-and-send behavior of the pickup notification use case. |
| [`validMailIsDecryptedForItsEventAndSentToTheRecipient()`](../../notification-worker/src/test/java/study/locker/notification/application/DeliverPickupNotificationTest.java#L27) | 1 | Expiry and successful decryption-and-send behavior of the pickup notification use case. |
| [`noticeExpiringAt(Instant)`](../../notification-worker/src/test/java/study/locker/notification/application/DeliverPickupNotificationTest.java#L35) | 1 | Expiry and successful decryption-and-send behavior of the pickup notification use case. |

## PickupMailInfrastructure

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class PickupMailInfrastructure`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailInfrastructure.java#L19) | 3 | Kafka, test secrets and mail-server endpoint wiring · Reading the Mailpit inbox for assertions · Access to original records on the mail dead-letter topic. |
| [`properties(DynamicPropertyRegistry)`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailInfrastructure.java#L32) | 1 | Kafka, test secrets and mail-server endpoint wiring. |
| [`inboxMessages()`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailInfrastructure.java#L41) | 1 | Reading the Mailpit inbox for assertions. |
| [`deadLetters()`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailInfrastructure.java#L50) | 1 | Access to original records on the mail dead-letter topic. |

**Refactor to consider.** Factor the inbox client and dead-letter reader away from container and property setup.

## PickupMailValidationIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class PickupMailValidationIT`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailValidationIT.java#L29) | 3 | Validation and safe diagnostics for malformed mail jobs · Rechecking expiry after recovery from SMTP outage · Kafka payload and delivery-counter fixtures used by mail scenarios. |
| [`realTime()`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailValidationIT.java#L38) | 1 | Rechecking expiry after recovery from SMTP outage. |
| [`count(String)`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailValidationIT.java#L43) | 1 | Kafka payload and delivery-counter fixtures used by mail scenarios. |
| [`parkedMailChecksExpiryAgainBeforeSending()`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailValidationIT.java#L48) | 1 | Rechecking expiry after recovery from SMTP outage. Advances the clock while SMTP is paused and checks that the recovered worker sends no expired mail. |
| [`invalidEventGoesStraightToDeadLettersAndOnlyTheValidMessageSendsMail()`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailValidationIT.java#L78) | 1 | Validation and safe diagnostics for malformed mail jobs. |
| [`message(String, String, String, Instant, String)`](../../notification-worker/src/test/java/study/locker/notification/integration/PickupMailValidationIT.java#L126) | 1 | Kafka payload and delivery-counter fixtures used by mail scenarios. |

**Refactor to consider.** Keep malformed-record assertions and expiry-during-outage assertions in independent scenarios with shared fixtures.
