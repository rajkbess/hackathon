package net.corda.derivativestradingnetwork.integrationTests

import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.junit.Test
import kotlin.test.assertEquals

class EndToEndTest {

    fun setUpEnvironmentAndRunTest(test : (DriverDSL, BnoNode, MemberNode, MemberNode, MemberNode, MemberNode, MemberNode)->Unit) {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states"))) {




            // ----- bno, client, dealer, ccp start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")),false)
            val client1 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C01", "", "US")), false)
            val dealer1 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D01", "", "US")), false)
            val client2 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C02", "", "US")), false)
            val dealer2 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D02", "", "US")), false)
            val ccp = MemberNode(this, TestIdentity(CordaX500Name("CCP-P01", "", "US")), false)

            listOf(bno, client1, dealer1, client2, dealer2, ccp).map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }

            //confirm all the nodes are on the network
            bno.confirmNodeIsOnTheNetwork()
            client1.confirmNodeIsOnTheNetwork()
            dealer1.confirmNodeIsOnTheNetwork()
            client2.confirmNodeIsOnTheNetwork()
            dealer2.confirmNodeIsOnTheNetwork()
            ccp.confirmNodeIsOnTheNetwork()


            //run the test
            test(this, bno, client1, client2, dealer1, dealer2, ccp)

        }
    }


    @Test
    fun `Nodes can ask for and get membership`() {
        val networkDefinition = EndToEndTest::class.java.getResource("/testData/network-definition.json").readText()
        setUpEnvironmentAndRunTest { _, bno, client1, client2, dealer1, dealer2, ccp ->
            //at the beginning there are no members
            assertEquals(0,bno.getMembershipStates().size)

            //clients ask for membership
            client1.askForMembership(networkDefinition)
            bno.approveMembership(client1.testIdentity.party)
            assertEquals(1,bno.getMembershipStates().size)

            client2.askForMembership(networkDefinition)
            bno.approveMembership(client2.testIdentity.party)
            assertEquals(2,bno.getMembershipStates().size)

            //dealers ask for membership
            dealer1.askForMembership(networkDefinition)
            bno.approveMembership(dealer1.testIdentity.party)
            assertEquals(3,bno.getMembershipStates().size)

            dealer2.askForMembership(networkDefinition)
            bno.approveMembership(dealer2.testIdentity.party)
            assertEquals(4,bno.getMembershipStates().size)

            //ccp asks for membership
            ccp.askForMembership(networkDefinition)
            bno.approveMembership(ccp.testIdentity.party)
            assertEquals(5,bno.getMembershipStates().size)

        }
    }


}