package net.corda.derivativestradingnetwork

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.member.RequestMembershipFlow
import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.core.flows.*
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.serialization.SerializationWhitelist
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
            val flowHandle = rpcOps.startTrackedFlow(::RequestMembershipFlow, MembershipMetadata(emptyMap(),"meh","quak","eeks"))
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(Response.Status.OK).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.message!!).build()
        }
    }

}

class WebPlugin : WebServerPluginRegistry {
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::WebApi))
}
