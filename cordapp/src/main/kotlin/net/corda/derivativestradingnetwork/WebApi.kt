package net.corda.derivativestradingnetwork

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.jaxrs.annotation.JacksonFeatures
import net.corda.businessnetworks.membership.member.GetMembersFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.cdmsupport.network.NetworkMap
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.derivativestradingnetwork.UtilParsers.Companion.parseMembershipDefinitionJson
import net.corda.derivativestradingnetwork.entity.PartyNameAndMembershipMetadata
import net.corda.derivativestradingnetwork.flow.*
import net.corda.webserver.services.WebServerPluginRegistry
import org.slf4j.Logger
import java.util.function.Function
import javax.ws.rs.*
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

    //######## CDM Events related REST endpoints ##########
    @POST
    @Path("persistCDMEvent")
    @Produces(MediaType.APPLICATION_JSON)
    fun persistCDMEvent(cdmEventJson: String): Response {
        return try {
            val networkMap = createNetworkMap()
            val flowHandle = rpcOps.startTrackedFlow(::PersistCDMEventOnLedgerFlow, cdmEventJson, networkMap)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private fun createNetworkMap() : NetworkMap {
        val flowHandle = rpcOps.startTrackedFlow(::GetMembersFlow,false)
        val members = flowHandle.returnValue.getOrThrow()

        val partyIdToCordaPartyMap = members.flatMap {
            val party = it.party
            it.membershipMetadata.partyIdAndAccountPairs.map { it.key to party }
        }.toMap()

        return NetworkMap(partyIdToCordaPartyMap)
    }

    //######## Vault query related REST endpoints #############
    @GET
    @Path("liveCDMContracts")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun liveCDMContracts() : Response {
        return createResponseToContractsQuery(VaultQueryType.LIVE_CONTRACTS)
    }

    @GET
    @Path("terminatedCDMContracts")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun terminatedCDMContracts() : Response {
        return createResponseToContractsQuery(VaultQueryType.TERMINATED_CONTRACTS)
    }

    @GET
    @Path("CDMResets")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun cDMResets(@QueryParam("contractId") contractId: String, @QueryParam("contractIdScheme") contractIdScheme: String,@QueryParam("issuer") issuer: String?,@QueryParam("partyReference") partyReference: String?) : Response {
        return createResponseToTargetedQuery(VaultTargetedQueryType.RESETS,contractId,contractIdScheme,issuer,partyReference)
    }

    @GET
    @Path("CDMPayments")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun cDMPayments(@QueryParam("contractId") contractId: String, @QueryParam("contractIdScheme") contractIdScheme: String,@QueryParam("issuer") issuer: String?,@QueryParam("partyReference") partyReference: String?) : Response {
        return createResponseToTargetedQuery(VaultTargetedQueryType.PAYMENTS,contractId,contractIdScheme,issuer,partyReference)
    }

    private fun createResponseToTargetedQuery(vaultTargetedQueryType: VaultTargetedQueryType, contractId: String, contractIdScheme: String, issuer: String?, partyReference: String?) : Response {
        return try {
            val flowHandle = rpcOps.startTrackedFlow(::VaultTargetedQueryFlow, vaultTargetedQueryType, contractId, contractIdScheme, issuer, partyReference)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity(result).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private fun createResponseToContractsQuery(vaultQueryType: VaultQueryType) : Response {
        return try {
            val flowHandle = rpcOps.startTrackedFlow(::VaultQueryFlow, vaultQueryType)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity(result).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    //######## Membership related REST endpoints #############
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
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun getMe() : Response {
        return try {
            val me = rpcOps.nodeInfo().legalIdentities[0].name
            Response.status(Response.Status.OK).entity(me).build()
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
        logger.info("Creating membership metadata from json $membershipDefinitionJson")
        val allMemberAccounts = parseMembershipDefinitionJson(membershipDefinitionJson)
        logger.info("Found ${allMemberAccounts.size} member accounts in the file")
        allMemberAccounts.map { it.name }.distinct().forEach { println("Account for $it found in the file") }
        val ourMemberAccounts = allMemberAccounts.filter { it.name == rpcOps.nodeInfo().legalIdentities.first().name.organisation }
        logger.info("Found ${ourMemberAccounts.size} member accounts in the file")
        val name = if (ourMemberAccounts.map { it.name }.distinct().size == 1) { ourMemberAccounts.first().name } else { throw InvalidMembershipMetadata("All accounts are expected to live under one name")}
        val role = if (ourMemberAccounts.map { it.type }.distinct().size == 1) { ourMemberAccounts.first().type } else { throw InvalidMembershipMetadata("All accounts are expected to live under one type")}
        val legalEntityId = if (ourMemberAccounts.map { it.legalEntityId }.distinct().size == 1) { ourMemberAccounts.first().legalEntityId } else { throw InvalidMembershipMetadata("All accounts are expected to live under one legal entity id")}
        val partyIdAndAccountPairs = ourMemberAccounts.map { it.partyId to it.account }.toMap()

        return MembershipMetadata(partyIdAndAccountPairs, legalEntityId, role, name)
    }
}

class WebPlugin : WebServerPluginRegistry {
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::WebApi))
}