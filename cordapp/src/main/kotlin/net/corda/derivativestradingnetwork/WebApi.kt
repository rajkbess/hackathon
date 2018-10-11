package net.corda.derivativestradingnetwork

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.jaxrs.annotation.JacksonFeatures
import net.corda.businessnetworks.membership.common.PartyAndMembershipMetadata
import net.corda.businessnetworks.membership.member.GetMembersFlow
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.cdmsupport.CDMContractState
import net.corda.cdmsupport.PaymentState
import net.corda.cdmsupport.ResetState
import net.corda.cdmsupport.network.NetworkMap
import net.corda.core.contracts.ContractState
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.derivativestradingnetwork.entity.PartyNameAndMembershipMetadata
import net.corda.derivativestradingnetwork.entity.SettlementInstruction
import net.corda.derivativestradingnetwork.flow.*
import net.corda.webserver.services.WebServerPluginRegistry
import org.slf4j.Logger
import java.time.LocalDate
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
    @Path("persistDraftCDMContract")
    @Produces(MediaType.APPLICATION_JSON)
    fun persistDraftCDMContract(cdmContractJson: String): Response {
        return try {
            val networkMap = createNetworkMap()
            val flowHandle = rpcOps.startTrackedFlow(::PersistDraftCDMContractOnLedgerFlow, cdmContractJson, networkMap)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @POST
    @Path("approveDraftCDMContract")
    @Produces(MediaType.APPLICATION_JSON)
    fun approveDraftCDMContract(@HeaderParam("contractId") contractId: String, @HeaderParam("contractIdScheme") contractIdScheme: String,@HeaderParam("issuer") issuer: String?,@HeaderParam("partyReference") partyReference: String?): Response {
        return try {
            val networkMap = createNetworkMap()
            val flowHandle = rpcOps.startTrackedFlow(::ApproveDraftCDMContractOnLedgerFlow, networkMap, contractId, contractIdScheme, issuer, partyReference, 1000)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @POST
    @Path("clearCDMContract")
    @Produces(MediaType.APPLICATION_JSON)
    fun clearCDMContract(@HeaderParam("contractId") contractId: String, @HeaderParam("contractIdScheme") contractIdScheme: String,@HeaderParam("issuer") issuer: String?,@HeaderParam("partyReference") partyReference: String?): Response {
        return try {
            val networkMap = createNetworkMap()
            val ccp = getPartiesOnThisBusinessNetwork("ccp").single().party
            val flowHandle = rpcOps.startTrackedFlow(::ClearCDMContractOnLedgerFlow, networkMap, ccp, contractId, contractIdScheme, issuer, partyReference)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @POST
    @Path("shareContract")
    @Produces(MediaType.APPLICATION_JSON)
    fun shareContract(@HeaderParam("shareWith") shareWith: String, @HeaderParam("contractId") contractId: String, @HeaderParam("contractIdScheme") contractIdScheme: String,@HeaderParam("issuer") issuer: String?,@HeaderParam("partyReference") partyReference: String?) : Response {
        return try {
            logger.info("Sharing contract id $contractId, contract id scheme $contractIdScheme with $shareWith")
            val party = getPartyFromThisBusinessNetwork(shareWith)
            val flowHandle = rpcOps.startTrackedFlow(::ShareContractFlow, party, contractId, contractIdScheme, issuer, partyReference)
            flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Contract shared with $shareWith.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private fun createNetworkMap() : NetworkMap {
        val flowHandle = rpcOps.startTrackedFlow(::GetMembersFlow,false)
        val members = flowHandle.returnValue.getOrThrow()

        val partyIdToCordaPartyMap = members.map { it.membershipMetadata.partyId to it.party }.toMap()

        return NetworkMap(partyIdToCordaPartyMap)
    }

    //######## Vault query related REST endpoints #############
    @GET
    @Path("liveCDMContracts")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun liveCDMContracts() : Response {
        return createResponseToQuery(VaultQueryType.LIVE_CONTRACTS)
    }

    @GET
    @Path("draftCDMContracts")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun draftCDMContracts() : Response {
        return createResponseToQuery(VaultQueryType.DRAFT_CONTRACTS)
    }

    @GET
    @Path("terminatedCDMContracts")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun terminatedCDMContracts() : Response {
        return createResponseToQuery(VaultQueryType.TERMINATED_CONTRACTS)
    }

    @GET
    @Path("novatedCDMContracts")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun novatedCDMContracts() : Response {
        return createResponseToQuery(VaultQueryType.NOVATED_CONTRACTS)
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

    @GET
    @Path("CDMPaymentsAll")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun cDMPaymentsAll() : Response {
        return createResponseToQuery(VaultQueryType.PAYMENTS)
    }

    @GET
    @Path("cdmContractsAudit")
    @Produces(MediaType.TEXT_PLAIN)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun cdmContractsAudit() : Response {
        return createResponseToContractsAuditQuery<CDMContractState>()
    }

    @GET
    @Path("cdmResetsAudit")
    @Produces(MediaType.TEXT_PLAIN)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun cdmResetsAudit() : Response {
        return createResponseToContractsAuditQuery<ResetState>()
    }

    @GET
    @Path("cdmPaymentsAudit")
    @Produces(MediaType.TEXT_PLAIN)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun cdmPaymentsAudit() : Response {
        return createResponseToContractsAuditQuery<PaymentState>()
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

    private fun createResponseToQuery(vaultQueryType: VaultQueryType) : Response {
        return try {
            val flowHandle = rpcOps.startTrackedFlow(::VaultQueryFlow, vaultQueryType)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity(result).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private inline fun <reified T : ContractState> createResponseToContractsAuditQuery() : Response {
        val allStatesCriteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL)
        val stateAndRefs = rpcOps.vaultQueryBy<T>(allStatesCriteria).states
        val states = stateAndRefs.map { it.state.data }
        return Response.status(Response.Status.OK).entity(states.toString()).build()
    }

    //######## Membership related REST endpoints #############
    @POST
    @Path("requestMembership")
    @Produces(MediaType.APPLICATION_JSON)
    fun requestMembership(membershipMetadata: MembershipMetadata): Response {
        return try {
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
            val parties = getPartyNamesOnThisBusinessNetwork()
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
            val clients = getPartyNamesOnThisBusinessNetwork("client")
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
            val clients = getPartyNamesOnThisBusinessNetwork("dealer")
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
            val clients = getPartyNamesOnThisBusinessNetwork("ccp")
            Response.status(Response.Status.OK).entity(clients).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @GET
    @Path("matchingServices")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun getMatchingServices() : Response {
        return try {
            val clients = getPartyNamesOnThisBusinessNetwork("matching service")
            Response.status(Response.Status.OK).entity(clients).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @GET
    @Path("regulators")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun getRegulators() : Response {
        return try {
            val clients = getPartyNamesOnThisBusinessNetwork("regulator")
            Response.status(Response.Status.OK).entity(clients).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private fun getPartyFromThisBusinessNetwork(name : String) : Party {
        val partiesOfThisName = getPartyNamesOnThisBusinessNetwork().filter { it.membershipMetadata.name == name }.map { CordaX500Name.parse(it.party) }
        val party = when {
            partiesOfThisName.isEmpty() -> throw PartyForThisNameNotFound(name)
            partiesOfThisName.size == 1 -> partiesOfThisName.first()
            else -> throw AmbiguousPartyName(name)
        }

        return rpcOps.wellKnownPartyFromX500Name(party) ?: throw PartyForThisCordaX500NameNotFound(party)
    }

    private fun getPartiesOnThisBusinessNetwork(role : String) : List<PartyAndMembershipMetadata> {
        return getPartiesOnThisBusinessNetwork().filter { it.membershipMetadata.role.equals(role,true) }
    }

    private fun getPartiesOnThisBusinessNetwork()  : List<PartyAndMembershipMetadata> {
        val flowHandle = rpcOps.startTrackedFlow(::GetMembersFlow,false)
        return flowHandle.returnValue.getOrThrow()
    }

    private fun getPartyNamesOnThisBusinessNetwork(role : String) : List<PartyNameAndMembershipMetadata> {
        return getPartyNamesOnThisBusinessNetwork().filter { it.membershipMetadata.role.equals(role,true) }
    }

    private fun getPartyNamesOnThisBusinessNetwork() : List<PartyNameAndMembershipMetadata> {
        val flowHandle = rpcOps.startTrackedFlow(::GetMembersFlow,false)
        return flowHandle.returnValue.getOrThrow().map { PartyNameAndMembershipMetadata(it.party.toString(),it.membershipMetadata) }
    }
}

class WebPlugin : WebServerPluginRegistry {
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::WebApi))
}