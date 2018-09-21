package net.corda.derivativestradingnetwork.integrationTests

import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.core.identity.CordaX500Name
import net.corda.derivativestradingnetwork.UtilParsers
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.isda.cdm.Event
import java.io.File
import kotlin.test.assertEquals

//You will struggle to run this from Intelli J on windows. On windows run the WrapperAroundNodeDriver as a JUnit test instead
fun main(args: Array<String>) {
    NodeDriverWithUseCase4().runNetwork()
}

class NodeDriverSandbox {
    fun runNetwork() {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true, waitForAllNodesToFinish = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.cdmsupport",
                        "net.corda.derivativestradingnetwork.flow",
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states",
                        "net.corda.yourcode"),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4))) {

            // ----- bno, client, dealer, ccp start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")), false)
            val client1 = MemberNode(this, TestIdentity(CordaX500Name("CLIENT-C01", "", "US")), false)


            val nonBnoNodes = listOf(client1)
            val nodes = listOf(bno) + nonBnoNodes

            nodes.map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }.forEach { it.confirmNodeIsOnTheNetwork() }
            println("Establishing business network")

            establishBusinessNetworkAndConfirmAssertions(bno, nonBnoNodes, 0, 1, 1, 0, 0)


            println("----- Network set up -----")
            nodes.forEach {
                println("${it.testIdentity.name.organisation} web url is ${it.webHandle.listenAddress}")
            }


            val titosPayment = readTextFromFile("/testData/useCase5Events/titosFile.json")
            client1.processSettlementInstruction(titosPayment)

        }
    }



    private fun establishBusinessNetworkAndConfirmAssertions(bno: BnoNode, membersToBe: List<MemberNode>, existingMembers : Int, expectedMembers : Int, expectedClients : Int, expectedDealers : Int, expectedCcps : Int) {
        val networkDefinition = getNetworkDefinitionJson()
        //at the beginning there are no members
        assertEquals(existingMembers, bno.getMembershipStates().size)

        membersToBe.forEach {
            acquireMembershipAndConfirmAssertions(bno, it, networkDefinition)
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it, expectedMembers, expectedClients, expectedDealers, expectedCcps) }
    }

    private fun getNetworkDefinitionJson() : String {
        return NodeDriver::class.java.getResource("/testData/network-definition-barclays-hackathon-with-additional-parties.json").readText()
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