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

    fun setUpEnvironmentAndRunTest(test : (DriverDSL, BnoNode, MemberNode, MemberNode, MemberNode, MemberNode, MemberNode)->Unit) {
        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
                extraCordappPackagesToScan = listOf(
                        "net.corda.cdmsupport",
                        "net.corda.derivativestradingnetwork.flow",
                        "net.corda.derivativestradingnetwork.states",
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
            val matchingService = MemberNode(this, TestIdentity(CordaX500Name("MATCHING-SERVICE-M01", "", "US")), false)
            val regulator = MemberNode(this, TestIdentity(CordaX500Name("REGULATOR-R01", "", "US")), false)

            listOf(bno,dealer1,dealer2, matchingService, ccp, regulator).map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }

            //confirm all the nodes are on the network
            bno.confirmNodeIsOnTheNetwork()
            dealer1.confirmNodeIsOnTheNetwork()
            dealer2.confirmNodeIsOnTheNetwork()
            ccp.confirmNodeIsOnTheNetwork()
            matchingService.confirmNodeIsOnTheNetwork()
            regulator.confirmNodeIsOnTheNetwork()

            establishBusinessNetworkAndConfirmAssertions(bno, listOf(dealer1,dealer2,ccp,matchingService,regulator))

            //run the test
            test(this, bno,  dealer1, dealer2, ccp, matchingService, regulator)

        }
    }

    @Test
    fun `Party A can propose a contract draft to Party B`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, _, _, _ ->
            val cdmContract = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            assertEquals(0, dealer1.getDraftContracts().size)
            assertEquals(0, dealer1.getDraftContracts().size)

            dealer1.persistDraftCDMContractOnLedger(cdmContract)

            assertEquals(1, dealer1.getDraftContracts().size)
            assertEquals(1, dealer2.getDraftContracts().size)
        }
    }

    @Test
    fun `Party B can accept Party A proposal`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, _, _, _ ->
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            val cdmContract2 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_2.json").readText()
            assertEquals(0, dealer1.getDraftContracts().size)
            assertEquals(0, dealer2.getDraftContracts().size)

            dealer1.persistDraftCDMContractOnLedger(cdmContract1)
            dealer1.persistDraftCDMContractOnLedger(cdmContract2)

            assertEquals(2, dealer1.getDraftContracts().size)
            assertEquals(2, dealer2.getDraftContracts().size)

            dealer2.approveDraftCDMContractOnLedger("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")

            assertEquals(1, dealer1.getDraftContracts().size)
            assertEquals(1, dealer2.getDraftContracts().size)

            assertEquals(1, dealer1.getLiveContracts().size)
            assertEquals(1, dealer2.getLiveContracts().size)

            dealer2.approveDraftCDMContractOnLedger("1234TradeId_2","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")

            assertEquals(0, dealer1.getDraftContracts().size)
            assertEquals(0, dealer2.getDraftContracts().size)

            assertEquals(2, dealer1.getLiveContracts().size)
            assertEquals(2, dealer2.getLiveContracts().size)
        }
    }

    @Test
    fun `Party cannot accept their own proposal`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, _, _, _ ->
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            assertNumbersOfContracts(listOf(dealer1,dealer2), 0, 0)
            dealer1.persistDraftCDMContractOnLedger(cdmContract1)
            assertNumbersOfContracts(listOf(dealer1,dealer2),1,0)
            dealer1.approveDraftCDMContractOnLedger("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",null,null,"We never proposed this draft")
        }
    }

    @Test
    fun `Party can get trade cleared`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, ccp, _, _ ->
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            assertNumbersOfContracts(listOf(dealer1,dealer2), 0, 0)
            dealer1.persistDraftCDMContractOnLedger(cdmContract1)
            assertNumbersOfContracts(listOf(dealer1,dealer2), 1, 0)
            dealer2.approveDraftCDMContractOnLedger("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertNumbersOfContracts(listOf(dealer1,dealer2),0,1)
            dealer1.clearCDMContract("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertNumbersOfContracts(listOf(dealer1,dealer2),0, 1, 1)
            assertNumbersOfContracts(ccp, 0, 2, 0)

            //look closer at the trades
            confirmTradeIdentity("1234TradeId_1_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer1.getLiveContracts().first() as Map<String,Any>)
            confirmTradeIdentity("1234TradeId_1_A","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer2.getLiveContracts().first() as Map<String,Any>)
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer1.getNovatedContracts().first() as Map<String,Any>)
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer2.getNovatedContracts().first() as Map<String,Any>)

            confirmTradeIdentity("1234TradeId_1_A","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",ccp.getLiveContracts()[0] as Map<String,Any>)
            confirmTradeIdentity("1234TradeId_1_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",ccp.getLiveContracts()[1] as Map<String,Any>)
        }
    }

    @Test
    fun `Clearing house will not accept notional over 1000000000`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, ccp, _, _ ->
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_4.json").readText()
            assertNumbersOfContracts(listOf(dealer1,dealer2), 0, 0)
            dealer1.persistDraftCDMContractOnLedger(cdmContract1)
            assertNumbersOfContracts(listOf(dealer1,dealer2), 1, 0)
            dealer2.approveDraftCDMContractOnLedger("1234TradeId_4","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertNumbersOfContracts(listOf(dealer1,dealer2),0,1)
            dealer1.clearCDMContract("1234TradeId_4","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",null,null,"exceeds limit")
            assertNumbersOfContracts(listOf(dealer1,dealer2),0, 1, 0)
            confirmTradeIdentity("1234TradeId_4","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer1.getLiveContracts().first() as Map<String,Any>)
            confirmTradeIdentity("1234TradeId_4","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer2.getLiveContracts().first() as Map<String,Any>)
            assertNumbersOfContracts(ccp, 0, 0, 0)
        }
    }

    @Test
    fun `Regulator gets to see all live trades`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, ccp, _, regulator ->
            //get it bilateral
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            assertNumbersOfContracts(regulator, 0, 0)
            dealer1.persistDraftCDMContractOnLedger(cdmContract1)
            assertNumbersOfContracts(regulator, 0, 0)
            dealer2.approveDraftCDMContractOnLedger("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertNumbersOfContracts(regulator, 0, 1)
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",regulator.getLiveContracts().first() as Map<String,Any>)

            //get it cleared
            dealer2.clearCDMContract("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertNumbersOfContracts(regulator, 0, 2, 1) //they have the novated too because they get the whole transaction
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",regulator.getNovatedContracts().first() as Map<String,Any>)
            confirmTradeIdentity("1234TradeId_1_A","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",regulator.getLiveContracts()[0] as Map<String,Any>)
            confirmTradeIdentity("1234TradeId_1_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",regulator.getLiveContracts()[1] as Map<String,Any>)
        }
    }

    private fun confirmTradeIdentity(contractId : String, contractIdScheme : String, trade : Map<String,Any>) {
        assertEquals(contractId, (((trade.get("contractIdentifier") as List<Map<String,Any>>).first().get("identifierValue")) as Map<String,Object>).get("identifier").toString())
        assertEquals(contractIdScheme, (((trade.get("contractIdentifier") as List<Map<String,Any>>).first().get("identifierValue")) as Map<String,Object>).get("identifierScheme").toString())
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
                it.testIdentity.name.organisation.contains("matching-service",true) -> "matching service"
                it.testIdentity.name.organisation.contains("regulator",true) -> "regulator"
                else -> throw RuntimeException("Role not recognized from organisation name")
            }
            acquireMembershipAndConfirmAssertions(bno,it,MembershipMetadata(role, it.testIdentity.name.organisation,it.testIdentity.name.organisation.hashCode().toString(),"Somewhere beyond the rainbow","Main Branch",it.testIdentity.name.organisation))
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it as MemberNode, 5, 0, 2, 1, 1) }
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

    private fun assertNumbersOfContracts(memberNodes : List<MemberNode>, draft : Int, live : Int = 0, novated : Int = 0) {
        memberNodes.forEach { assertNumbersOfContracts(it, draft, live, novated) }
    }

    private fun assertNumbersOfContracts(memberNode : MemberNode, draft : Int, live : Int = 0, novated : Int = 0) {
        assertEquals(draft, memberNode.getDraftContracts().size)
        assertEquals(live, memberNode.getLiveContracts().size)
        assertEquals(novated, memberNode.getNovatedContracts().size)
    }
}