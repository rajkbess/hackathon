package net.corda.derivativestradingnetwork.integrationTests

import net.corda.businessnetworks.membership.states.MembershipMetadata
import net.corda.cdmsupport.eventparsing.parseContractFromJson
import net.corda.core.identity.CordaX500Name
import net.corda.derivativestradingnetwork.entity.CompressionRequest
import net.corda.derivativestradingnetwork.entity.ContractIdAndContractIdScheme
import net.corda.derivativestradingnetwork.entity.ContractStatus
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import org.isda.cdm.Contract
import org.isda.cdm.ResetPrimitive
import org.isda.cdm.StateEnum
import org.junit.Test
import java.lang.RuntimeException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            val oracle = MemberNode(this, TestIdentity(CordaX500Name("ORACLE-O01", "", "US")), false)

            listOf(bno,dealer1,dealer2, matchingService, ccp, regulator, oracle).map { it.startCoreAsync() }.map { it.waitForCoreToStart() }.map { it.startWebAsync() }.map { it.waitForWebToStart() }

            //confirm all the nodes are on the network
            bno.confirmNodeIsOnTheNetwork()
            dealer1.confirmNodeIsOnTheNetwork()
            dealer2.confirmNodeIsOnTheNetwork()
            ccp.confirmNodeIsOnTheNetwork()
            matchingService.confirmNodeIsOnTheNetwork()
            regulator.confirmNodeIsOnTheNetwork()
            oracle.confirmNodeIsOnTheNetwork()

            establishBusinessNetworkAndConfirmAssertions(bno, listOf(dealer1,dealer2,ccp,matchingService,regulator,oracle))

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
            dealer1.approveDraftCDMContractOnLedger("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/","We never proposed this draft")
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
            confirmTradeIdentity("1234TradeId_1_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer1.getLiveContracts().first())
            confirmTradeIdentity("1234TradeId_1_A","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer2.getLiveContracts().first())
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer1.getNovatedContracts().first())
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer2.getNovatedContracts().first())

            confirmTradeIdentity("1234TradeId_1_A","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",ccp.getLiveContracts()[0])
            confirmTradeIdentity("1234TradeId_1_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",ccp.getLiveContracts()[1])
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
            dealer1.clearCDMContract("1234TradeId_4","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/","exceeds limit")
            assertNumbersOfContracts(listOf(dealer1,dealer2),0, 1, 0)
            confirmTradeIdentity("1234TradeId_4","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer1.getLiveContracts().first())
            confirmTradeIdentity("1234TradeId_4","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",dealer2.getLiveContracts().first())
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
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",regulator.getLiveContracts().first())

            //get it cleared
            dealer2.clearCDMContract("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertNumbersOfContracts(regulator, 0, 2, 1) //they have the novated too because they get the whole transaction
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",regulator.getNovatedContracts().first())
            confirmTradeIdentity("1234TradeId_1_A","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",regulator.getLiveContracts()[0])
            confirmTradeIdentity("1234TradeId_1_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",regulator.getLiveContracts()[1])
        }
    }

    @Test
    fun `Cleared trades can be compressed`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, ccp, _, regulator ->
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            val cdmContract2 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_2.json").readText()
            val cdmContract3 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_3.json").readText()
            val cdmContract5 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_5.json").readText()

            insertTradeBilaterallyAndClearAndConfirmAssertions(cdmContract1, dealer1, dealer2, ccp)
            insertTradeBilaterallyAndClearAndConfirmAssertions(cdmContract2, dealer1, dealer2, ccp)
            insertTradeBilaterallyAndClearAndConfirmAssertions(cdmContract3, dealer1, dealer2, ccp)
            insertTradeBilaterallyAndClearAndConfirmAssertions(cdmContract5, dealer1, dealer2, ccp)

            assertNumbersOfContracts(listOf(dealer1, dealer2),0,4, 4, 0)
            assertNumbersOfContracts(ccp, 0, 8, 0, 0)
            assertNumbersOfContracts(regulator, 0, 8, 4, 0)

            dealer1.compressCDMContractsOnLedger(CompressionRequest(
                    listOf(
                            ContractIdAndContractIdScheme("1234TradeId_1_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/"),
                            ContractIdAndContractIdScheme("1234TradeId_2_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/"),
                            ContractIdAndContractIdScheme("1234TradeId_3_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
                    )))

            assertNumbersOfContracts(dealer1,0,2, 4, 3)
            assertNumbersOfContracts(dealer2, 0, 4, 4, 0)
            assertNumbersOfContracts(ccp, 0, 6, 0, 3)
            assertNumbersOfContracts(regulator, 0, 6, 4, 3)

            //dealer 1 has two live trades, one the one not touched by compression and the other one the compressed one
            val unaffectedTrade = dealer1.getLiveContracts()[0]
            val compressedTrade = dealer1.getLiveContracts()[1]
            confirmTradeIdentity("1234TradeId_5_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",unaffectedTrade)
            confirmTradeIdentity("CMP-","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",compressedTrade, true)
            confirmTradeNotional(compressedTrade,604750000)
        }
    }

    @Test
    fun `Bilateral trades can be compressed`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, _, _, regulator ->
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            val cdmContract2 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_2.json").readText()
            val cdmContract3 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_3.json").readText()
            val cdmContract5 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_5.json").readText()

            insertTradeBilaterallyAndConfirmAssertions(cdmContract1, dealer1, dealer2)
            insertTradeBilaterallyAndConfirmAssertions(cdmContract2, dealer1, dealer2)
            insertTradeBilaterallyAndConfirmAssertions(cdmContract3, dealer1, dealer2)
            insertTradeBilaterallyAndConfirmAssertions(cdmContract5, dealer1, dealer2)

            assertNumbersOfContracts(listOf(dealer1, dealer2, regulator),0,4, 0, 0)

            dealer1.compressCDMContractsOnLedger(CompressionRequest(
                    listOf(
                            ContractIdAndContractIdScheme("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/"),
                            ContractIdAndContractIdScheme("1234TradeId_2","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/"),
                            ContractIdAndContractIdScheme("1234TradeId_3","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
                    )))

            assertNumbersOfContracts(listOf(dealer1, dealer2, regulator),0,2, 0, 3)

            //dealer 1 has two live trades, one the one not touched by compression and the other one the compressed one
            val unaffectedTrade = dealer1.getLiveContracts()[0]
            val compressedTrade = dealer1.getLiveContracts()[1]
            confirmTradeIdentity("1234TradeId_5","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",unaffectedTrade)
            confirmTradeIdentity("CMP-","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",compressedTrade,true)
            confirmTradeNotional(compressedTrade,604750000)
        }
    }

    @Test
    fun `One function to pull all trades can be used`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, ccp, _, _ ->
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            val cdmContract2 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_2.json").readText()
            val cdmContract3 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_3.json").readText()
            val cdmContract5 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_5.json").readText()

            insertTradeBilaterallyAndConfirmAssertions(cdmContract1, dealer1, dealer2)
            insertTradeBilaterallyAndClearAndConfirmAssertions(cdmContract2, dealer1, dealer2, ccp)
            insertTradeBilaterallyAndConfirmAssertions(cdmContract5, dealer1, dealer2)
            dealer1.persistDraftCDMContractOnLedger(cdmContract3)
            dealer1.compressCDMContractsOnLedger(CompressionRequest(
                    listOf(
                            ContractIdAndContractIdScheme("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/"),
                            ContractIdAndContractIdScheme("1234TradeId_5","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
                    )))

            val allContracts = dealer1.getAllContracts()
            assertEquals(1, allContracts.filter { it.contractStatus == ContractStatus.NOVATED }.size)
            assertEquals(2, allContracts.filter { it.contractStatus == ContractStatus.LIVE }.size)
            assertEquals(1, allContracts.filter { it.contractStatus == ContractStatus.DRAFT }.size)
            assertEquals(2, allContracts.filter { it.contractStatus == ContractStatus.TERMINATED }.size)
        }
    }

    @Test
    fun `Contract parents can be pulled`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, ccp, _, regulator ->
            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()

            //make a bilateral trade
            insertTradeBilaterallyAndConfirmAssertions(cdmContract1, dealer1, dealer2)

            //check parents of this bilateral live trade
            val parents1 = dealer1.getContractParents("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",null)
            assertEquals(0, parents1.size)

            //clear the trade
            dealer1.clearCDMContract("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            //check parents of the novated version of the trade
            val parents2 = dealer1.getContractParents("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/", StateEnum.NOVATED)
            assertEquals(1, parents2.size)
            //check the parents of the live version of the trade
            val parents3 = dealer1.getContractParents("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/", null)
            assertEquals(0, parents3.size)
            //check the parents of the new trade with the ccp
            val parentsOfCleared = dealer1.getContractParents("1234TradeId_1_B","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/", null)
            assertEquals(1, parentsOfCleared.size)

            //put another trade in and compress with 1
            val cdmContract2 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_2.json").readText()
            insertTradeBilaterallyAndClearAndConfirmAssertions(cdmContract2, dealer1, dealer2, ccp)
            dealer1.compressCDMContractsOnLedger(CompressionRequest(
                    listOf(
                            ContractIdAndContractIdScheme("1234TradeId_1_B"),
                            ContractIdAndContractIdScheme("1234TradeId_2_B")
                    )))

            val compressedContractIdentifier = dealer1.getLiveContracts().single().contractIdentifier.first().identifierValue.identifier
            val parentsOfCompressed = dealer1.getContractParents(compressedContractIdentifier,"http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/", null)
            assertEquals(2, parentsOfCompressed.size)

            val parentsOfClearedInTerminatedState = dealer1.getContractParents("1234TradeId_1_B", "http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/", StateEnum.TERMINATED)
            assertEquals(2, parentsOfClearedInTerminatedState.size)

            val parentsOfClearedInLiveState = dealer1.getContractParents("1234TradeId_1_B", "http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/", null)
            assertEquals(1, parentsOfClearedInLiveState.size)
            confirmTradeIdentity("1234TradeId_1","http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/",parentsOfClearedInLiveState.single())
        }
    }

    @Test
    fun `Basis contract can be fixed`() {
        setUpEnvironmentAndRunTest { _, _, dealer1, dealer2, _, _, regulator ->

            val cdmContract1 = EndToEndTest::class.java.getResource("/testData/lchDemo/dealer-1_dealer-2/cdmContract_1.json").readText()
            insertTradeBilaterallyAndConfirmAssertions(cdmContract1, dealer1, dealer2)

            //fix on effective date
            val fixingDate = LocalDate.parse("2018-10-19")

            dealer1.fixCDMContractsOnLedger(fixingDate)

            var resetsOnDealer1 = dealer1.getResets("1234TradeId_1", "http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertEquals(2, resetsOnDealer1.size)

            var resetsOnDealer2 = dealer2.getResets("1234TradeId_1", "http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertEquals(2, resetsOnDealer2.size)

            confirmReset(resetsOnDealer1[0], fixingDate, BigDecimal("1.12345"))
            confirmReset(resetsOnDealer1[1], fixingDate, BigDecimal("1.12345"))

            //fix on month later
            val fixingDateOneMonthLater = LocalDate.parse("2018-11-18")

            dealer2.fixCDMContractsOnLedger(fixingDateOneMonthLater)

            resetsOnDealer1 = dealer1.getResets("1234TradeId_1", "http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertEquals(3, resetsOnDealer1.size)

            resetsOnDealer2 = dealer2.getResets("1234TradeId_1", "http://www.fpml.org/coding-scheme/external/unique-transaction-identifier/")
            assertEquals(3, resetsOnDealer2.size)

            confirmReset(resetsOnDealer1[2], fixingDateOneMonthLater, BigDecimal("1.12345"))
        }
    }

    private fun confirmReset(resetPrimitive : ResetPrimitive, fixingDate : LocalDate, fixingRate : BigDecimal) {
        assertEquals(fixingDate, resetPrimitive.date)
        assertEquals(fixingRate, resetPrimitive.resetValue)
        assertTrue(resetPrimitive.cashflow.cashflowAmount != null)
    }

    //fixed float and only doing those that apply

    private fun confirmTradeNotional(contract : Contract, expectedNotional : Long) {
        contract.contractualProduct.economicTerms.payout.interestRatePayout.forEach {
            assertEquals(it.quantity.notionalSchedule.notionalStepSchedule.initialValue.toLong(), expectedNotional)
        }
    }


    private fun confirmTradeIdentity(contractId : String, contractIdScheme : String, contract : Contract, contractIdPrefixOnly : Boolean = false) {
        val actualContractId = contract.contractIdentifier.first().identifierValue.identifier
        val actualContractIdScheme = contract.contractIdentifier.first().identifierValue.identifierScheme
        if(contractIdPrefixOnly) {
            assertTrue(actualContractId.startsWith(contractId))
        } else {
            assertEquals(actualContractId, contractId)
        }

        assertEquals(actualContractIdScheme, contractIdScheme)
    }

    private fun insertTradeBilaterallyAndClearAndConfirmAssertions(cdmContractJson : String, party1 : MemberNode, party2 : MemberNode, ccp : MemberNode) {
        val party1LiveBefore = party1.getLiveContracts().size
        val party1NovatedBefore = party1.getNovatedContracts().size
        val party2LiveBefore = party2.getLiveContracts().size
        val party2NovatedBefore = party2.getNovatedContracts().size
        val ccpLiveBefore = ccp.getLiveContracts().size

        val cdmContract = parseContractFromJson(cdmContractJson)
        val contractId = cdmContract.contractIdentifier.single().identifierValue.identifier
        val contractIdScheme = cdmContract.contractIdentifier.single().identifierValue.identifierScheme
        party1.persistDraftCDMContractOnLedger(cdmContractJson)
        party2.approveDraftCDMContractOnLedger(contractId, contractIdScheme)
        party1.clearCDMContract(contractId,contractIdScheme)

        assertEquals(party1LiveBefore + 1, party1.getLiveContracts().size)
        assertEquals(party1NovatedBefore + 1, party1.getNovatedContracts().size)

        assertEquals(party2LiveBefore + 1, party2.getLiveContracts().size)
        assertEquals(party2NovatedBefore + 1, party2.getNovatedContracts().size)

        assertEquals(ccpLiveBefore + 2, ccp.getLiveContracts().size)
    }

    private fun insertTradeBilaterallyAndConfirmAssertions(cdmContractJson : String, party1 : MemberNode, party2 : MemberNode) {
        val party1LiveBefore = party1.getLiveContracts().size
        val party2LiveBefore = party2.getLiveContracts().size

        val cdmContract = parseContractFromJson(cdmContractJson)
        val contractId = cdmContract.contractIdentifier.single().identifierValue.identifier
        val contractIdScheme = cdmContract.contractIdentifier.single().identifierValue.identifierScheme

        party1.persistDraftCDMContractOnLedger(cdmContractJson)
        party2.approveDraftCDMContractOnLedger(contractId, contractIdScheme)

        assertEquals(party1LiveBefore + 1, party1.getLiveContracts().size)
        assertEquals(party2LiveBefore + 1, party2.getLiveContracts().size)
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
                it.testIdentity.name.organisation.contains("oracle",true) -> "oracle"
                else -> throw RuntimeException("Role not recognized from organisation name")
            }
            acquireMembershipAndConfirmAssertions(bno,it,MembershipMetadata(role, it.testIdentity.name.organisation,it.testIdentity.name.organisation.hashCode().toString(),"Somewhere beyond the rainbow","Main Branch",it.testIdentity.name.organisation))
        }

        //check members can see one another
        membersToBe.forEach { confirmVisibility(it as MemberNode, 6, 0, 2, 1, 1, 1) }
    }

    private fun acquireMembershipAndConfirmAssertions(bno : BnoNode, member : MemberNode, membershipMetadata : MembershipMetadata) {
        val membershipsBefore = bno.getMembershipStates().size
        member.askForMembership(membershipMetadata)
        bno.approveMembership(member.testIdentity.party)
        assertEquals(membershipsBefore+1,bno.getMembershipStates().size)
    }

    private fun confirmVisibility(memberNode : MemberNode, allMembers : Int, clients : Int, dealers : Int, ccps : Int, regulators : Int, oracles : Int) {
        assertEquals(allMembers,memberNode.getMembersVisibleToNode().size)
        assertEquals(clients,memberNode.getMembersVisibleToNode("clients").size)
        assertEquals(dealers,memberNode.getMembersVisibleToNode("dealers").size)
        assertEquals(ccps,memberNode.getMembersVisibleToNode("ccps").size)
        assertEquals(regulators,memberNode.getMembersVisibleToNode("regulators").size)
        assertEquals(oracles,memberNode.getMembersVisibleToNode("oracles").size)
    }

    private fun assertNumbersOfContracts(memberNodes : List<MemberNode>, draft : Int, live : Int = 0, novated : Int = 0, terminated : Int = 0) {
        memberNodes.forEach { assertNumbersOfContracts(it, draft, live, novated, terminated) }
    }

    private fun assertNumbersOfContracts(memberNode : MemberNode, draft : Int, live : Int = 0, novated : Int = 0, terminated : Int = 0) {
        assertEquals(draft, memberNode.getDraftContracts().size,"Failed on ${memberNode.testIdentity.name.organisation}")
        assertEquals(live, memberNode.getLiveContracts().size,"Failed on ${memberNode.testIdentity.name.organisation}")
        assertEquals(novated, memberNode.getNovatedContracts().size,"Failed on ${memberNode.testIdentity.name.organisation}")
        assertEquals(terminated, memberNode.getTerminatedContracts().size,"Failed on ${memberNode.testIdentity.name.organisation}")
    }
}