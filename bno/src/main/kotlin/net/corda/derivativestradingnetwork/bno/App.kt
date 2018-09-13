package net.corda.derivativestradingnetwork.bno

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.jaxrs.annotation.JacksonFeatures
import net.corda.businessnetworks.membership.bno.ActivateMembershipForPartyFlow
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.webserver.services.WebServerPluginRegistry
import org.slf4j.Logger
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
@Path("bnoApi")
class WebApi(val rpcOps: CordaRPCOps) {

    companion object {
        private val logger: Logger = loggerFor<WebApi>()
    }

    @GET
    @Path("membershipStates")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun getMembershipStates(): Response {
        return try {
            logger.info("Returning all states")
            val membershipStates = rpcOps.vaultQuery(Membership.State::class.java).states
            Response.status(Response.Status.OK).entity(membershipStates).build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    @POST
    @Path("activateMembership")
    @Produces(MediaType.APPLICATION_JSON)
    @JacksonFeatures(serializationEnable = arrayOf(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
    fun activateMembership(name : String): Response {
        return try {
            logger.info("Looking for party $name")
            val party = findPartyForName(name)
            val flowHandle = rpcOps.startTrackedFlow(::ActivateMembershipForPartyFlow,party)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

    private fun findPartyForName(name : String) : Party {
        return rpcOps.wellKnownPartyFromX500Name(CordaX500Name.parse(name)) ?: throw PartyNotFoundException(name)
    }

}

class WebPlugin : WebServerPluginRegistry {
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::WebApi))
}
