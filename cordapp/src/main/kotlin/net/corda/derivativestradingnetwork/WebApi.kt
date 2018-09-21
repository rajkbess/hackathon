package net.corda.derivativestradingnetwork

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.jaxrs.annotation.JacksonFeatures
import com.google.gson.Gson
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
import net.corda.derivativestradingnetwork.UtilParsers.Companion.parseMembershipDefinitionJson
import net.corda.derivativestradingnetwork.entity.PartyNameAndMembershipMetadata
import net.corda.derivativestradingnetwork.entity.SettlementInstruction
import net.corda.derivativestradingnetwork.flow.*
import net.corda.webserver.services.WebServerPluginRegistry
import org.isda.cdm.Contract
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

    @POST
    @Path("processSettlementInstruction")
    @Produces(MediaType.APPLICATION_JSON)
    fun processSettlementInstruction(settlementInstructionJson: String): Response {
        return try {
            val settlementInstructions = createSettlementInstructions(settlementInstructionJson)
            throw UnsupportedOperationException()
            /*
            val networkMap = createNetworkMap()
            val flowHandle = rpcOps.startTrackedFlow(::PersistCDMEventOnLedgerFlow, cdmEventJson, networkMap)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Transaction id ${result.id} committed to ledger.\n").build()
            */
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private fun createSettlementInstructions(settlementInstructionJson: String) : List<SettlementInstruction> {
        val rawData = Gson().fromJson(settlementInstructionJson,List::class.java)
        return rawData.map { it as Map<String,Object> }.map {
            val receiverPartyId = it.get("receiverPartyId") as String
            val receiverAccountId = it.get("receiverAccountId") as String
            val receiverName = it.get("receiverName") as String
            val payerPartyId = it.get("payerPartyId") as String
            val payerAccountId = it.get("payerAccountId") as String
            val payerName = it.get("payerName") as String
            val amount = it.get("amount") as Double
            val currency = it.get("currency") as String
            val paymentDate = LocalDate.parse(it.get("paymentDate") as String)
            val settlementReference = it.get("settlementConfirmation") as String

            SettlementInstruction(receiverPartyId,receiverAccountId,receiverName,payerPartyId,payerAccountId,payerName,amount,currency,paymentDate,settlementReference)
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
        return createResponseToQuery(VaultQueryType.LIVE_CONTRACTS)
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

    @GET
    @Path("regulators")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun getRegulators() : Response {
        return try {
            val clients = getPartiesOnThisBusinessNetwork("regulator")
            Response.status(Response.Status.OK).entity(clients).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private fun getPartyFromThisBusinessNetwork(name : String) : Party {
        val partiesOfThisName = getPartiesOnThisBusinessNetwork().filter { it.membershipMetadata.name == name }.map { CordaX500Name.parse(it.party) }
        val party = when {
            partiesOfThisName.isEmpty() -> throw PartyForThisNameNotFound(name)
            partiesOfThisName.size == 1 -> partiesOfThisName.first()
            else -> throw AmbiguousPartyName(name)
        }

        return rpcOps.wellKnownPartyFromX500Name(party) ?: throw PartyForThisCordaX500NameNotFound(party)
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
        val myOrganisation = rpcOps.nodeInfo().legalIdentities.first().name.organisation
        val ourMemberAccounts = allMemberAccounts.filter { it.name ==  myOrganisation}
        logger.info("Found ${ourMemberAccounts.size} member accounts in the file")
        val name = if (ourMemberAccounts.map { it.name }.distinct().size == 1) { ourMemberAccounts.first().name } else { throw InvalidMembershipMetadata("All accounts are expected to live under one name")}
        val role = if (ourMemberAccounts.map { it.type }.distinct().size == 1) { ourMemberAccounts.first().type } else { throw InvalidMembershipMetadata("All accounts are expected to live under one type")}
        val legalEntityId = ourMemberAccounts.map { it.legalEntityId }.distinct()
        val partyIdAndAccountPairs = ourMemberAccounts.map { it.partyId to it.account }.toMap()

        return MembershipMetadata(partyIdAndAccountPairs, legalEntityId, role, name)
    }
}

class WebPlugin : WebServerPluginRegistry {
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::WebApi))
}