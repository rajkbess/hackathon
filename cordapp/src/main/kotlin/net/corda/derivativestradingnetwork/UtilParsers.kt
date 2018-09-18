package net.corda.derivativestradingnetwork

import com.google.common.reflect.TypeToken
import com.google.gson.*
import net.corda.derivativestradingnetwork.entity.MemberAccountDefinition
import java.lang.reflect.Type
import java.time.Instant

class UtilParsers {

    companion object {

        fun parseMembershipDefinitionJson(membershipDefinitionJson : String) : List<MemberAccountDefinition> {
            val desiredType = object : TypeToken<List<MemberAccountDefinition>>() {}.type
            return getSuitableGson().fromJson<List<MemberAccountDefinition>>(membershipDefinitionJson,desiredType)
        }

        fun getSuitableGson() : Gson {
            return GsonBuilder().registerTypeAdapter(Instant::class.java, object : JsonSerializer<Instant> {

                override fun serialize(src: Instant?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
                    return JsonPrimitive(src?.epochSecond ?: 0)
                }

            }).registerTypeAdapter(Instant::class.java, object : JsonDeserializer<Instant> {
                override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Instant {
                    return Instant.ofEpochSecond(json!!.asLong)
                }
            }).create()
        }

    }


}