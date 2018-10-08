package net.corda.derivativestradingnetwork.integrationTests

import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.junit.Test
import java.lang.RuntimeException
import kotlin.test.assertEquals

class EndToEndTest {

    fun setUpEnvironmentAndRunTest(test : (DriverDSL, BnoNode, MemberNode, MemberNode, MemberNode)->Unit) {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.cdmsupport",
                        "net.corda.derivativestradingnetwork.flow",
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states"),
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4))) {




            // ----- bno, client, dealer, ccp start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")),false)
            val dealer1 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D01", "", "US")), false)
            val dealer2 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D02", "", "US")), false)
            val ccp = MemberNode(this, TestIdentity(CordaX500Name("CCP-P01", "", "US")), false)

            listOf(bno,dealer1,dealer2,ccp).map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }

            //confirm all the nodes are on the network
            bno.confirmNodeIsOnTheNetwork()
            dealer1.confirmNodeIsOnTheNetwork()
            dealer2.confirmNodeIsOnTheNetwork()
            ccp.confirmNodeIsOnTheNetwork()

            establishBusinessNetworkAndConfirmAssertions(bno, listOf(dealer1,dealer2,ccp))

            //run the test
            test(this, bno,  dealer1, dealer2, ccp)

        }
    }

    @Test
    fun `Test membershpips`() {
        setUpEnvironmentAndRunTest { _, bno, dealer1, dealer2, ccp ->

        }
    }

    private fun establishBusinessNetworkAndConfirmAssertions(bno : BnoNode, membersToBe : List<MemberNode>) {
        //at the beginning there are no members
        assertEquals(0,bno.getMembershipStates().size)

        membersToBe.forEach {
            val role = when {
                it.testIdentity.name.organisation.contains("client",true) -> "client"
                it.testIdentity.name.organisation.contains("dealer",true) -> "dealer"
                it.testIdentity.name.organisation.contains("ccp",true) -> "ccp"
                it.testIdentity.name.organisation.contains("regulator",true) -> "regulator"
                else -> throw RuntimeException("Role not recognized from organisation name")
            }
            acquireMembershipAndConfirmAssertions(bno,it,MembershipMetadata(role, it.testIdentity.name.organisation,it.testIdentity.name.organisation.hashCode().toString(),"Somewhere beyond the rainbow","Main Branch",it.testIdentity.name.organisation.hashCode().toString()))
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it as MemberNode, 3, 0, 2, 1, 0) }
    }

    private fun acquireMembershipAndConfirmAssertions(bno : BnoNode, member : MemberNode, membershipMetadata : MembershipMetadata) {
        val membershipsBefore = bno.getMembershipStates().size
        member.askForMembership(membershipMetadata)
        bno.approveMembership(member.testIdentity.party)
        assertEquals(membershipsBefore+1,bno.getMembershipStates().size)
    }

    private fun confirmVisibility(memberNode : MemberNode, allMembers : Int, clients : Int, dealers : Int, ccps : Int, regulators : Int) {
        assertEquals(allMembers,memberNode.getMembersVisibleToNode().size)
        assertEquals(clients,memberNode.getMembersVisibleToNode("clients").size)
        assertEquals(dealers,memberNode.getMembersVisibleToNode("dealers").size)
        assertEquals(ccps,memberNode.getMembersVisibleToNode("ccps").size)
        assertEquals(regulators,memberNode.getMembersVisibleToNode("regulators").size)
    }
}