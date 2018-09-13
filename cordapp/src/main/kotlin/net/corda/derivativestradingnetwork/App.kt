package net.corda.derivativestradingnetwork

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.jaxrs.annotation.JacksonFeatures
import com.google.common.reflect.TypeToken
import com.google.gson.*
import net.corda.businessnetworks.membership.common.PartyAndMembershipMetadata
import net.corda.businessnetworks.membership.member.GetMembersFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.derivativestradingnetwork.entity.MemberAccountDefinition
import net.corda.derivativestradingnetwork.entity.PartyNameAndMembershipMetadata
import net.corda.webserver.services.WebServerPluginRegistry
import org.slf4j.Logger
import java.lang.reflect.Type
import java.time.Instant
import java.util.function.Function
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// *****************
// * API Endpoints *
// *****************
@Path("memberApi")
class WebApi(val rpcOps: CordaRPCOps) {

    companion object {
        private val logger: Logger = loggerFor<WebApi>()
    }

    @POST
    @Path("requestMembership")
    @Produces(MediaType.APPLICATION_JSON)
    fun requestMembership(membershipDefinitionJson: String): Response {
        return try {
            val membershipMetadata = createMembershipMetadata(membershipDefinitionJson)
            val flowHandle = rpcOps.startTrackedFlow(::RequestMembershipFlow, membershipMetadata)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @GET
    @Path("members")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun getMembers() : Response {
        return try {
            val parties = getPartiesOnThisBusinessNetwork()
            Response.status(Response.Status.OK).entity(parties).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @GET
    @Path("clients")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun getClients() : Response {
        return try {
            val clients = getPartiesOnThisBusinessNetwork("client")
            Response.status(Response.Status.OK).entity(clients).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @GET
    @Path("dealers")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun getDealers() : Response {
        return try {
            val clients = getPartiesOnThisBusinessNetwork("dealer")
            Response.status(Response.Status.OK).entity(clients).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @GET
    @Path("ccps")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun getCcps() : Response {
        return try {
            val clients = getPartiesOnThisBusinessNetwork("ccp")
            Response.status(Response.Status.OK).entity(clients).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private fun getPartiesOnThisBusinessNetwork(role : String) : List<PartyNameAndMembershipMetadata> {
        return getPartiesOnThisBusinessNetwork().filter { it.membershipMetadata.role.equals(role,true) }
    }

    private fun getPartiesOnThisBusinessNetwork() : List<PartyNameAndMembershipMetadata> {
        val flowHandle = rpcOps.startTrackedFlow(::GetMembersFlow,false)
        return flowHandle.returnValue.getOrThrow().map { PartyNameAndMembershipMetadata(it.party.toString(),it.membershipMetadata) }
    }

    private fun createMembershipMetadata(membershipDefinitionJson: String) : MembershipMetadata {
        val ourMemberAccounts = parseMembershipDefinitionJson(membershipDefinitionJson).filter { it.name == rpcOps.nodeInfo().legalIdentities.first().name.organisation }
        val name = if (ourMemberAccounts.map { it.name }.distinct().size == 1) { ourMemberAccounts.first().name } else { throw InvalidMembershipMetadata("All accounts are expected to live under one name")}
        val role = if (ourMemberAccounts.map { it.type }.distinct().size == 1) { ourMemberAccounts.first().type } else { throw InvalidMembershipMetadata("All accounts are expected to live under one type")}
        val legalEntityId = if (ourMemberAccounts.map { it.legalEntityId }.distinct().size == 1) { ourMemberAccounts.first().legalEntityId } else { throw InvalidMembershipMetadata("All accounts are expected to live under one legal entity id")}
        val partyIdAndAccountPairs = ourMemberAccounts.map { it.partyId to it.account }.toMap()

        return MembershipMetadata(partyIdAndAccountPairs, legalEntityId, role, name)
    }

    private fun parseMembershipDefinitionJson(membershipDefinitionJson : String) : List<MemberAccountDefinition> {
        val desiredType = object : TypeToken<List<MemberAccountDefinition>>() {}.type
        return getSuitableGson().fromJson<List<MemberAccountDefinition>>(membershipDefinitionJson,desiredType)
    }

    protected fun getSuitableGson() : Gson {
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

class WebPlugin : WebServerPluginRegistry {
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::WebApi))
}
