package net.corda.derivativestradingnetwork.integrationTests

import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import java.io.File
import kotlin.test.assertEquals

//You will struggle to run this from Intelli J on windows. On windows run the WrapperAroundNodeDriver as a JUnit test instead
fun main(args: Array<String>) {
    NodeDriver().runNetwork()
}

class NodeDriver {
    fun runNetwork() {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true, waitForAllNodesToFinish = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.cdmsupport",
                        "net.corda.derivativestradingnetwork.flow",
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states"),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4))) {

            // ----- bno, client, dealer, ccp start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")), false)
            val client1 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C01", "", "US")), false)
            val client2 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C02", "", "US")), false)
            val client3 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C03", "", "US")), false)
            val client4 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C04", "", "US")), false)
            val client5 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C05", "", "US")), false)
            val dealer1 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D01", "", "US")), false)
            val dealer2 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D02", "", "US")), false)
            val dealer3 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D03", "", "US")), false)
            val ccp = MemberNode(this, TestIdentity(CordaX500Name("CCP-P01", "", "US")), false)

            val nonBnoNodes = listOf(client1, client2, client3, client4, client5, dealer1, dealer2, dealer3, ccp)
            val nodes = listOf(bno) + nonBnoNodes

            nodes.map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.forEach { it.waitForWebToStart() }
            nodes.forEach { node -> node.confirmNodeIsOnTheNetwork() }
            establishBusinessNetworkAndConfirmAssertions(bno, nonBnoNodes)

            println("Network set up")
        }
    }

    private fun establishBusinessNetworkAndConfirmAssertions(bno: BnoNode, membersToBe: List<MemberNode>) {
        val networkDefinition = getNetworkDefinitionJson()
        //at the beginning there are no members
        assertEquals(0, bno.getMembershipStates().size)

        membersToBe.forEach {
            acquireMembershipAndConfirmAssertions(bno, it, networkDefinition)
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it as MemberNode, 9, 5, 3, 1) }
    }

    private fun feedInTradesFromDirectoryAndConfirmAssertions(directoryWithTrades : File) {
        directoryWithTrades.listFiles { file, name -> name.endsWith(".json",true)}.forEach {
            val event = parseEventFromJson(it.readText())
            val partyOne = event.party[0].partyId.first() //only one party initiates the process of putting the trade on the ledger. the other party will sign this


        }
    }

    private fun createMapFromPartyIdToName(networkDefinition : String) {

    }


    private fun getNetworkDefinitionJson() : String {
        return NodeDriver::class.java.getResource("/testData/network-definition.json").readText()
    }

    private fun acquireMembershipAndConfirmAssertions(bno: BnoNode, member: MemberNode, networkDefinition: String) {
        val membershipsBefore = bno.getMembershipStates().size
        member.askForMembership(networkDefinition)
        bno.approveMembership(member.testIdentity.party)
        assertEquals(membershipsBefore + 1, bno.getMembershipStates().size)
    }

    private fun confirmVisibility(memberNode: MemberNode, allMembers: Int, clients: Int, dealers: Int, ccps: Int) {
        assertEquals(allMembers, memberNode.getMembersVisibleToNode().size)
        assertEquals(clients, memberNode.getMembersVisibleToNode("clients").size)
        assertEquals(dealers, memberNode.getMembersVisibleToNode("dealers").size)
        assertEquals(ccps, memberNode.getMembersVisibleToNode("ccps").size)
    }
}