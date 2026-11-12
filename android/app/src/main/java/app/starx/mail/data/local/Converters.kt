package app.starx.mail.data.local

import androidx.room.TypeConverter
import app.starx.mail.core.model.DeliveryStatus
import app.starx.mail.core.model.Domain
import app.starx.mail.core.model.EmailFolder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromDomainList(value: List<Domain>?): String {
        return gson.toJson(value ?: emptyList<Domain>())
    }

    @TypeConverter
    fun toDomainList(value: String?): List<Domain> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Domain>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromFolder(folder: EmailFolder?): String {
        return folder?.id ?: EmailFolder.INBOX.id
    }

    @TypeConverter
    fun toFolder(id: String?): EmailFolder {
        return id?.let { EmailFolder.fromId(it) } ?: EmailFolder.INBOX
    }

    @TypeConverter
    fun fromDeliveryStatus(status: DeliveryStatus?): String {
        return status?.name ?: DeliveryStatus.RECEIVED.name
    }

    @TypeConverter
    fun toDeliveryStatus(name: String?): DeliveryStatus {
        return DeliveryStatus.fromString(name)
    }
}
