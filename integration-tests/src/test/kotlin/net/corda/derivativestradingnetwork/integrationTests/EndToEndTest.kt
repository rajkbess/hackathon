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
            acquireMembershipAndConfirmAssertions(bno,client1,networkDefinition)
            acquireMembershipAndConfirmAssertions(bno,client2,networkDefinition)

            //dealers ask for membership
            acquireMembershipAndConfirmAssertions(bno,dealer1,networkDefinition)
            acquireMembershipAndConfirmAssertions(bno,dealer2,networkDefinition)

            //ccp asks for membership
            acquireMembershipAndConfirmAssertions(bno, ccp, networkDefinition)

            //check members can see one another
            listOf(client1,client2,dealer1,dealer2).forEach { confirmVisibility(it as MemberNode, 5, 2, 2, 1) }
        }
    }

    private fun acquireMembershipAndConfirmAssertions(bno : BnoNode, member : MemberNode, networkDefinition : String) {
        val membershipsBefore = bno.getMembershipStates().size
        member.askForMembership(networkDefinition)
        bno.approveMembership(member.testIdentity.party)
        assertEquals(membershipsBefore+1,bno.getMembershipStates().size)
    }

    private fun confirmVisibility(memberNode : MemberNode, allMembers : Int, clients : Int, dealers : Int, ccps : Int) {
        assertEquals(allMembers,memberNode.getMembersVisibleToNode().size)
        assertEquals(clients,memberNode.getMembersVisibleToNode("clients").size)
        assertEquals(dealers,memberNode.getMembersVisibleToNode("dealers").size)
        assertEquals(ccps,memberNode.getMembersVisibleToNode("ccps").size)
    }


}