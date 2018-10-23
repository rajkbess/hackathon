package net.corda.derivativestradingnetwork.integrationTests

import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.core.identity.CordaX500Name
import net.corda.derivativestradingnetwork.UtilParsers
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.isda.cdm.Event
import java.io.File
import java.lang.RuntimeException
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
                        "net.corda.derivativestradingnetwork.states",
                        "net.corda.businessnetworks.membership.member.service",
                        "net.corda.businessnetworks.membership.member",
                        "net.corda.businessnetworks.membership.bno",
                        "net.corda.businessnetworks.membership.states",
                        "net.corda.yourcode"),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4))) {

            // ----- bno, client, dealer, ccp start nodes -------
            val bno = BnoNode(this, TestIdentity(CordaX500Name("BNO", "New York", "US")), false)
            val dealer1 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D01", "", "US")), false)
            val dealer2 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D02", "", "US")), false)
            val dealer3 = MemberNode(this, TestIdentity(CordaX500Name("DEALER-D03", "", "US")), false)
            val ccp = MemberNode(this, TestIdentity(CordaX500Name("CCP-P01", "", "US")), false)
            val regulator = MemberNode(this, TestIdentity(CordaX500Name("REGULATOR-R01", "", "US")), false)
            val oracle = MemberNode(this, TestIdentity(CordaX500Name("ORACLE-O01", "", "US")), false)

            val nonBnoNodes = listOf(dealer1, dealer2, dealer3, ccp, regulator, oracle)
            val nodes = listOf(bno) + nonBnoNodes

            nodes.map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }.forEach { it.confirmNodeIsOnTheNetwork() }
            println("Establishing business network")

            establishBusinessNetworkAndConfirmAssertions(bno, nonBnoNodes - dealer3, 0, 5, 0, 2, 1,0,1, 1)

            putSomeTradesOnTheNetwork(dealer1, dealer2, ccp)

            println("----- Network set up -----")
            nodes.forEach {
                println("${it.testIdentity.name.organisation} web url is ${it.webHandle.listenAddress}")
            }
        }
    }

    private fun putSomeTradesOnTheNetwork(dealer1 : MemberNode, dealer2 : MemberNode, ccp : MemberNode) {
        val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
        val cdmContract2 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_2.json").readText()
        val cdmContract3 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_3.json").readText()
        assertEquals(0, dealer1.getDraftContracts().size)
        assertEquals(0, dealer2.getDraftContracts().size)

        dealer1.persistDraftCDMContractOnLedger(cdmContract1)
        dealer1.persistDraftCDMContractOnLedger(cdmContract2)
        dealer2.persistDraftCDMContractOnLedger(cdmContract3)

        assertEquals(3, dealer1.getDraftContracts().size)
        assertEquals(3, dealer2.getDraftContracts().size)

        dealer2.approveDraftCDMContractOnLedger("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
        dealer1.approveDraftCDMContractOnLedger("1234TradeId_3","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")

        assertEquals(1, dealer1.getDraftContracts().size)
        assertEquals(1, dealer2.getDraftContracts().size)

        assertEquals(2, dealer1.getLiveContracts().size)
        assertEquals(2, dealer2.getLiveContracts().size)
        assertEquals(0, ccp.getLiveContracts().size)

        dealer1.clearCDMContract("1234TradeId_3","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")

        assertEquals(2, dealer1.getLiveContracts().size)
        assertEquals(2, dealer2.getLiveContracts().size)
        assertEquals(1, dealer1.getNovatedContracts().size)
        assertEquals(1, dealer2.getNovatedContracts().size)
        assertEquals(2, ccp.getLiveContracts().size)

        println("Test trades uploaded")
    }

    private fun establishBusinessNetworkAndConfirmAssertions(bno: BnoNode, membersToBe: List<MemberNode>, existingMembers : Int, expectedMembers : Int, expectedClients : Int, expectedDealers : Int, expectedCcps : Int, expectedMatchingServices : Int, expectedRegulators : Int, expectedOracles : Int) {
        //at the beginning there are no members
        assertEquals(existingMembers, bno.getMembershipStates().size)

        membersToBe.forEach {
            val role = when {
                it.testIdentity.name.organisation.contains("client",true) -> "client"
                it.testIdentity.name.organisation.contains("dealer",true) -> "dealer"
                it.testIdentity.name.organisation.contains("ccp",true) -> "ccp"
                it.testIdentity.name.organisation.contains("regulator",true) -> "regulator"
                it.testIdentity.name.organisation.contains("matching-service",true) -> "matching service"
                it.testIdentity.name.organisation.contains("oracle",true) -> "oracle"
                else -> throw RuntimeException("Role not recognized from organisation name")
            }
            val membershipMetadata = MembershipMetadata(role, it.testIdentity.name.organisation,it.testIdentity.name.organisation.hashCode().toString(),"Somewhere beyond the rainbow","Main Branch",it.testIdentity.name.organisation)
            acquireMembershipAndConfirmAssertions(bno, it, membershipMetadata)
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it, expectedMembers, expectedClients, expectedDealers, expectedCcps, expectedMatchingServices, expectedRegulators, expectedOracles) }
    }


    private fun acquireMembershipAndConfirmAssertions(bno: BnoNode, member: MemberNode, membershipMetadata: MembershipMetadata) {
        val membershipsBefore = bno.getMembershipStates().size
        member.askForMembership(membershipMetadata)
        bno.approveMembership(member.testIdentity.party)
        assertEquals(membershipsBefore + 1, bno.getMembershipStates().size)
    }

    private fun confirmVisibility(memberNode: MemberNode, allMembers: Int, clients: Int, dealers: Int, ccps: Int, matchingServices : Int, regulators : Int, oracles : Int) {
        assertEquals(allMembers, memberNode.getMembersVisibleToNode().size)
        assertEquals(clients, memberNode.getMembersVisibleToNode("clients").size)
        assertEquals(dealers, memberNode.getMembersVisibleToNode("dealers").size)
        assertEquals(ccps, memberNode.getMembersVisibleToNode("ccps").size)
        assertEquals(matchingServices, memberNode.getMembersVisibleToNode("matchingServices").size)
        assertEquals(regulators, memberNode.getMembersVisibleToNode("regulators").size)
        assertEquals(oracles, memberNode.getMembersVisibleToNode("oracles").size)
    }
}