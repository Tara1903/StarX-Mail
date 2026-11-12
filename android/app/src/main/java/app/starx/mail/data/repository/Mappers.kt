package app.starx.mail.data.repository

import app.starx.mail.core.model.*
import app.starx.mail.data.local.entity.*
import app.starx.mail.data.remote.dto.*

fun EmailEntity.toDomain(attachments: List<EmailAttachment> = emptyList()): Email {
    return Email(
        id = id,
        inboxId = inboxId,
        threadId = threadId,
        from = fromAddress,
        to = toAddresses,
        cc = ccAddresses,
        bcc = bccAddresses,
        replyTo = replyTo,
        subject = subject,
        textBody = textBody,
        htmlBody = htmlBody,
        snippet = snippet,
        createdAt = createdAt,
        folder = folder,
        isRead = isRead,
        isStarred = isStarred,
        hasAttachments = hasAttachments,
        attachments = attachments,
        deliveryStatus = deliveryStatus
    )
}

fun Email.toEntity(): EmailEntity {
    return EmailEntity(
        id = id,
        inboxId = inboxId,
        threadId = threadId,
        fromAddress = from,
        toAddresses = to,
        ccAddresses = cc,
        bccAddresses = bcc,
        replyTo = replyTo,
        subject = subject,
        textBody = textBody,
        htmlBody = htmlBody,
        snippet = snippet,
        createdAt = createdAt,
        folder = folder,
        isRead = isRead,
        isStarred = isStarred,
        hasAttachments = hasAttachments,
        deliveryStatus = deliveryStatus
    )
}

fun EmailDto.toDomain(): Email {
    val attList = attachments.map { it.toDomain() }
    return Email(
        id = id,
        inboxId = inboxId,
        threadId = threadId,
        from = from,
        to = to,
        cc = cc,
        bcc = bcc,
        replyTo = replyTo,
        subject = subject,
        textBody = textBody,
        htmlBody = htmlBody,
        snippet = snippet,
        createdAt = createdAt,
        folder = EmailFolder.fromId(folder),
        isRead = isRead,
        isStarred = isStarred,
        hasAttachments = hasAttachments,
        attachments = attList,
        deliveryStatus = DeliveryStatus.fromString(deliveryStatus),
        headers = headers
    )
}

fun EmailDto.toEntity(): EmailEntity {
    return EmailEntity(
        id = id,
        inboxId = inboxId,
        threadId = threadId,
        fromAddress = from,
        toAddresses = to,
        ccAddresses = cc,
        bccAddresses = bcc,
        replyTo = replyTo,
        subject = subject,
        textBody = textBody,
        htmlBody = htmlBody,
        snippet = snippet,
        createdAt = createdAt,
        folder = EmailFolder.fromId(folder),
        isRead = isRead,
        isStarred = isStarred,
        hasAttachments = hasAttachments || attachments.isNotEmpty(),
        deliveryStatus = DeliveryStatus.fromString(deliveryStatus)
    )
}

fun AttachmentDto.toDomain(): EmailAttachment {
    return EmailAttachment(
        id = id,
        filename = filename,
        contentType = contentType,
        size = size,
        contentBase64 = contentBase64
    )
}

fun AttachmentEntity.toDomain(): EmailAttachment {
    return EmailAttachment(
        id = id,
        filename = filename,
        contentType = contentType,
        size = size,
        contentBase64 = contentBase64,
        localUri = localUri
    )
}

fun DraftEntity.toDomain(attachments: List<EmailAttachment> = emptyList()): Draft {
    return Draft(
        id = id,
        inboxId = inboxId,
        to = toAddresses,
        cc = ccAddresses,
        bcc = bccAddresses,
        subject = subject,
        textBody = textBody,
        htmlBody = htmlBody,
        attachments = attachments,
        updatedAt = updatedAt
    )
}

fun Draft.toEntity(): DraftEntity {
    return DraftEntity(
        id = id,
        inboxId = inboxId,
        toAddresses = to,
        ccAddresses = cc,
        bccAddresses = bcc,
        subject = subject,
        textBody = textBody,
        htmlBody = htmlBody,
        updatedAt = updatedAt
    )
}

fun DraftDto.toDomain(): Draft {
    return Draft(
        id = id,
        inboxId = inboxId,
        to = to,
        cc = cc,
        bcc = bcc,
        subject = subject,
        textBody = textBody,
        htmlBody = htmlBody,
        attachments = attachments.map { it.toDomain() },
        updatedAt = updatedAt
    )
}

fun AccountDto.toDomain(): Account {
    return Account(
        id = id,
        email = email,
        connected = connected,
        resendKeyMasked = resendKeyMasked,
        defaultFromAddress = defaultFromAddress,
        domains = domains.map { it.toDomain() },
        inboxes = inboxes.map { it.toDomain() },
        webhookConfigured = webhookConfigured,
        webhookUrl = webhookUrl,
        createdAt = createdAt,
        lastSyncedAt = lastSyncedAt
    )
}

fun DomainDto.toDomain(): Domain {
    return Domain(
        id = id,
        name = name,
        status = status,
        region = region,
        records = records.map {
            DomainDnsRecord(
                record = it.record,
                name = it.name,
                type = it.type,
                value = it.value,
                ttl = it.ttl,
                status = it.status,
                priority = it.priority
            )
        },
        createdAt = createdAt
    )
}

fun InboxDto.toDomain(): Inbox {
    return Inbox(
        id = id,
        emailAddress = emailAddress,
        name = name,
        fromName = fromName,
        forwarding = forwarding,
        status = status,
        createdAt = createdAt
    )
}

fun InboxEntity.toDomain(): Inbox {
    return Inbox(
        id = id,
        emailAddress = emailAddress,
        name = name,
        fromName = fromName,
        forwarding = forwarding,
        status = status,
        createdAt = createdAt
    )
}

fun Inbox.toEntity(): InboxEntity {
    return InboxEntity(
        id = id,
        emailAddress = emailAddress,
        name = name,
        fromName = fromName,
        forwarding = forwarding,
        status = status,
        createdAt = createdAt
    )
}

fun AccountEntity.toDomain(inboxes: List<Inbox> = emptyList()): Account {
    return Account(
        id = id,
        email = email,
        connected = connected,
        resendKeyMasked = resendKeyMasked,
        defaultFromAddress = defaultFromAddress,
        domains = domains,
        inboxes = inboxes,
        webhookConfigured = webhookConfigured,
        webhookUrl = webhookUrl,
        createdAt = createdAt,
        lastSyncedAt = lastSyncedAt
    )
}

fun Account.toEntity(): AccountEntity {
    return AccountEntity(
        id = id,
        email = email ?: defaultFromAddress,
        connected = connected,
        resendKeyMasked = resendKeyMasked,
        defaultFromAddress = defaultFromAddress,
        domains = domains,
        webhookConfigured = webhookConfigured,
        webhookUrl = webhookUrl,
        createdAt = createdAt,
        lastSyncedAt = lastSyncedAt
    )
}
