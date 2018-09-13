package net.corda.derivativestradingnetwork.integrationTests

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.junit.Test
import kotlin.test.assertEquals

class EndToEndTest {

    fun setUpEnvironmentAndRunTest(test : (DriverDSL, BnoNode, MemberNode, MemberNode, MemberNode)->Unit) {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states"))) {




            // ----- bno, client, dealer, ccp start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")),false)
            val client = MemberNode(this, TestIdentity(CordaX500Name("Client", "", "US")), false)
            val dealer = MemberNode(this, TestIdentity(CordaX500Name("Dealer", "", "US")), false)
            val ccp = MemberNode(this, TestIdentity(CordaX500Name("CCP", "", "US")), false)

            listOf(bno, client, dealer, ccp).map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }

            //confirm all the nodes are on the network
            bno.confirmNodeIsOnTheNetwork()
            client.confirmNodeIsOnTheNetwork()
            dealer.confirmNodeIsOnTheNetwork()
            ccp.confirmNodeIsOnTheNetwork()


            //run the test
            test(this, bno, client, dealer, ccp)

        }
    }


    @Test
    fun `Nodes can ask for and get membership`() {
        setUpEnvironmentAndRunTest { _, bno, client, dealer, ccp ->
            //at the beginning there are no members
            assertEquals(0,bno.getMembershipStates().size)

            //client asks for membership
            client.askForMembership("Client","Alternative Name")
            bno.approveMembership(client.testIdentity.party)
            assertEquals(1,bno.getMembershipStates().size)

            //dealer asks for membership
            dealer.askForMembership("Dealer","Alternative Name")
            bno.approveMembership(dealer.testIdentity.party)
            assertEquals(2,bno.getMembershipStates().size)

            //ccp asks for membership
            ccp.askForMembership("CCP","Alternative Name")
            bno.approveMembership(ccp.testIdentity.party)
            assertEquals(3,bno.getMembershipStates().size)

        }
    }


}