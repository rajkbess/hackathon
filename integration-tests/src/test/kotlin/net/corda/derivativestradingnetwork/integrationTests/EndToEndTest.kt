package net.corda.derivativestradingnetwork.integrationTests

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.FungibleAsset
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.errors.AddressBindingException
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import net.corda.testing.node.User
import org.assertj.core.api.Assertions
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.assertEquals

class EndToEndTest {

    fun setUpEnvironmentAndRunTest(test : (DriverDSL, BnoNode, MemberNode)->Unit) {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states"))) {




            // ----- bno, customer, bank, datastore and attester start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")),false)
            val client = MemberNode(this, TestIdentity(CordaX500Name("Client", "", "US")), false)

            listOf(bno, client).map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }

            //confirm all the nodes are on the network
            bno.confirmNodeIsOnTheNetwork()
            client.confirmNodeIsOnTheNetwork()


            //run the test
            test(this, bno, client)

        }
    }


    @Test
    fun `Nodes can ask for and get membership`() {
        setUpEnvironmentAndRunTest { _, bno, client ->
            //ask for membership
            assertEquals(0,bno.getMembershipStates().size)
            client.askForMembership("Some Customer","meh")
            bno.approveMembership(client.testIdentity.party)
            assertEquals(1,bno.getMembershipStates().size)
        }
    }


}