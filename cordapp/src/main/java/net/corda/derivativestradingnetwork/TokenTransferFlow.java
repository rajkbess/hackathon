package net.corda.derivativestradingnetwork;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.NetworkMapCache;
import net.corda.core.node.services.VaultService;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.derivativestradingnetwork.states.MoneyToken;

import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

@InitiatingFlow
@StartableByRPC
public class TokenTransferFlow extends FlowLogic<Void> {
    private final Currency currency;
    private final int amount;
    private final Party newOwner;

    private final ProgressTracker progressTracker = new ProgressTracker();

    public TokenTransferFlow(Currency currency, int amount, Party newOwner) {
        this.currency = currency;
        this.amount = amount;
        this.newOwner = newOwner;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        NetworkMapCache networkMap = getServiceHub().getNetworkMapCache();
        Party notary = networkMap.getNotaryIdentities().get(0);

        VaultService vault = getServiceHub().getVaultService();
        List<StateAndRef<MoneyToken.State>> stateAndRefs =
                vault.queryBy(MoneyToken.State.class).getStates();

        List<StateAndRef<MoneyToken.State>> matchingStates = stateAndRefs.stream().filter(stateAndRef -> {
            MoneyToken.State state = stateAndRef.getState().getData();
            Boolean isCorrectAmount = state.getAmount() == amount;
            Boolean isCorrectCurrency = state.getCurrency().equals(currency);
            return isCorrectAmount && isCorrectCurrency;
        }).collect(Collectors.toList());

        if (matchingStates.isEmpty())
            throw new IllegalArgumentException("No matching token to transfer.");
        StateAndRef<MoneyToken.State> inputStateAndRef = matchingStates.get(0);
        MoneyToken.State inputState = inputStateAndRef.getState().getData();

        MoneyToken.State outputToken =
                new MoneyToken.State(amount, currency, inputState.getIssuer(), newOwner, null);
        CommandData transferCommand = new MoneyToken.Commands.Transfer();

        TransactionBuilder txBuilder = new TransactionBuilder(notary);
        txBuilder.addInputState(inputStateAndRef);
        txBuilder.addOutputState(outputToken, MoneyToken.Companion.getCONTRACT_NAME());
        txBuilder.addCommand(transferCommand, getOurIdentity().getOwningKey());

        txBuilder.verify(getServiceHub());

        SignedTransaction stx = getServiceHub().signInitialTransaction(txBuilder);

        subFlow(new FinalityFlow(stx));

        return null;
    }
}