# Running the nodes

1. Deploy the nodes: `gradlew.bat deployNodes` (or `./gradlew deployNodes` on osX)
2. Build nodes: `build/nodes/runnodes`

# Interacting with the nodes

See the tokens:

    run vaultQuery contractStateType: net.corda.derivativestradingnetwork.states.MoneyToken$State

Try and issue too much:

    flow start UserIssuanceRequestFlow amount: 1100, currency: EUR, ourBank: commercial-bank

Try and transfer a token you don't have:

    flow start TokenTransferFlow currency: EUR, amount: 750, newOwner: user-two

Issue the correct amount:

    flow start UserIssuanceRequestFlow amount: 900, currency: EUR, ourBank: commercial-bank
   
Transfer a token you have:

    flow start TokenTransferFlow currency: EUR, amount: 900, newOwner: user-two
