package net.corda.derivativestradingnetwork.flow

import co.paralleluniverse.fibers.Suspendable
import com.google.common.collect.ImmutableList
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.derivativestradingnetwork.states.MoneyToken
import java.security.PublicKey
import java.util.*

@InitiatingFlow
@StartableByRPC
class TokenTransferFlow(private val currency: Currency, private val amount: Int, private val newOwner: Party) : FlowLogic<Unit>() {

    override val progressTracker: ProgressTracker? = ProgressTracker()

    @Suspendable
    override fun call(): Unit {
        val networkMap = serviceHub.networkMapCache
        val notary = networkMap.notaryIdentities[0]

        val vault = serviceHub.vaultService
        val stateAndRefs = vault.queryBy(MoneyToken.State::class.java).states

        val matchingStates = stateAndRefs.filter { (state) ->
            val state = state.data
            val isCorrectAmount = state.amount == amount.toLong()
            val isCorrectCurrency = state.currency == currency
            isCorrectAmount && isCorrectCurrency
        }

        if (matchingStates.isEmpty())
            throw IllegalArgumentException("No matching token to transfer.")
        val inputStateAndRef = matchingStates[0]
        val (_, _, issuer, _, amlAuthority) = inputStateAndRef.state.data

        val outputToken = MoneyToken.State(amount.toLong(), currency, issuer, newOwner, amlAuthority)
        val transferCommand = MoneyToken.Commands.Transfer()

        val requiredSigners: List<PublicKey>
        if (amount > 150) {
            requiredSigners = Arrays.asList(ourIdentity.owningKey, outputToken.amlAuthority.owningKey)
        } else {
            requiredSigners = listOf(ourIdentity.owningKey)
        }

        val txBuilder = TransactionBuilder(notary)
                .addInputState(inputStateAndRef)
                .addOutputState(outputToken, MoneyToken.CONTRACT_NAME)
                .addCommand(transferCommand, requiredSigners)

        txBuilder.verify(serviceHub)

        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        if (amount > 150) {
            val sessionWithAml = initiateFlow(outputToken.amlAuthority)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, ImmutableList.of(sessionWithAml)))
            subFlow(FinalityFlow(fullySignedTx))
        } else {
            subFlow(FinalityFlow(partSignedTx))
        }
    }
}