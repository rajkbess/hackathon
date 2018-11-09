package net.corda.derivativestradingnetwork;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

@InitiatedBy(TokenTransferFlow.class)
public class TokenTransferFlowResponder extends FlowLogic<SignedTransaction> {

    private final FlowSession otherPartyFlow;

    public TokenTransferFlowResponder(FlowSession otherPartyFlow) {
        this.otherPartyFlow = otherPartyFlow;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                super(otherPartyFlow, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) {

            }
        }

        return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
    }
}