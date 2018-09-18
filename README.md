![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Derivatives Trading Network

## Description

This is a template project for working with the CDM in Corda. This template allows you to easily create a business 
network of Corda nodes governed by a Business Network Operator. Each node is assigned the role of client, dealer or 
central counterparty. Each node will have the following installed:

* Corda 4
* The Corda CDM libraries that allow you to interact with the CDM from Corda
* A `PersistCDMEventOnLedgerFlow` showing you how to use the Corda CDM libraries to persist CDM events to the ledger
* A web API for interacting with the nodes to allow them to perform CDM operations

You are expected to extend this template as part of the Barclays DerivHack hackathon. You should extend this template 
by adding code in the following packages:

* `cordapp-contracts-states/src/main/java/net/corda/yourcode`, for new states/contracts written in Java
* `cordapp-contracts-states/src/main/kotlin/net/corda/yourcode`, for new states/contracts written in Kotlin
* `cordapp/src/main/java/net/corda/yourcode`, for other new classes written in Java
* `cordapp/src/main/kotlin/net/corda/yourcode`, for other new classes written in Kotlin

## Running the network

There are three ways of setting up the network:

* Run all nodes and their webservers in one JVM from IntelliJ
* Run all nodes in their separate JVMs
* In the cloud

It is recommended to use the first option because it is the fastest, the least resource-intensive, it allows you to 
debug into the running nodes during development, and it automates the process of adding the nodes to the business 
network and adding the Barclays trades to the ledger.

Because the network is large (10 nodes), the second option may fail with an out-of-memory error.

### Run all nodes and their webservers in one JVM from IntelliJ

* Mac: run the "Mac Only: Run Network" run configuration
* Windows: run the "Windows: Run Network" JUnit test run configuration

This will start the Barclays clients, dealers, CCP and the BNO. It will also set up the memberships in the business 
network and place all the Barclays trades on the ledger.

If you do not wish to place all the Barclays trades on the ledger, comment out the call to 
`feedInTradesFromDirectoryAndConfirmAssertions` in 
`integration-tests/src/test/kotlin/net/corda/derivativestradingnetwork/NodeDriver.kt`.

### Run all nodes in their separate JVMs

Follow the instructions here: 
https://docs.corda.net/tutorial-cordapp.html#running-the-example-cordapp-from-the-terminal. In summary, on Mac:

* `./gradlew clean deployNodes`
* ``build/nodes/runnodes``

And on Windows:

* `gradlew clean deployNodes`
* ``build\nodes\runnodes``

Each node other than the BNO needs to request membership, which is then approved by the BNO. To quickly run these steps 
for all nodes:

```bash
cd deploy 
./joinNetwork.sh 
```

### In the cloud

A process for quickly deploying to a cloud server is documented in the 'deploy' folder, simply refer to 
the README. Alternatively try editing 'build.gradle' to remove some of the nodes (_will need to rerun 
the steps above afterwards_).  

As when using `deployNodes` + `runNodes`, each node other than the BNO needs to request membership, which is then 
approved by the BNO. To quickly run these steps for all nodes:
                    
```bash
cd deploy 
./joinNetwork.sh 
```

## Interacting with the nodes

Once running the full network will then have 1 BNO, 1 CCP, 5 clients and 3 dealers nodes running. Each of these nodes 
represents one legal entity.

### Node URLs

When running the nodes using the Node Driver, the ports allocated to the nodes are non-deterministic. However, the 
author always ended up with the nodes being accessible at the following addresses:

* BNO-DTN:      `http://localhost:10043`
* CLIENT-C01:   `http://localhost:10040`
* CLIENT-C02:   `http://localhost:10037`
* CLIENT-C03:   `http://localhost:10034`
* CLIENT-C04:   `http://localhost:10031`
* CLIENT-C05:   `http://localhost:10028`
* DEALER-D01:   `http://localhost:10025`
* DEALER-D02:   `http://localhost:10022`
* DEALER-D03:   `http://localhost:10019`
* CCP-P01:      `http://localhost:10016`

When running the nodes using `deployNodes` + `runNodes`, the nodes will be accessible at the following addresses:

* BNO-DTN:      `http://localhost:10013`
* CLIENT-C01:   `http://localhost:10023`
* CLIENT-C02:   `http://localhost:10033`
* CLIENT-C03:   `http://localhost:10043`
* CLIENT-C04:   `http://localhost:10053`
* CLIENT-C05:   `http://localhost:10063`
* DEALER-D01:   `http://localhost:10073`
* DEALER-D02:   `http://localhost:10083`
* DEALER-D03:   `http://localhost:10093`
* CCP-P01:      `http://localhost:10103`

You can check the identity of each node by hitting the `http://localhost:100XX/api/memberApi/me` endpoint.

### Node endpoints

You interact with each node using the following endpoints:

* POST `memberApi/persistCDMEvent` - Writes a CDM event to the ledger. Takes the following body params:
    * `cdmEventJson` (see `integration-tests/src/test/resources/testData/cdmEvents/dealer-1_client-4/newTrade_1.json` for an example)

* GET `memberApi/liveCDMContracts` - Returns a list of the node's live contracts
* GET `memberApi/terminatedCDMContracts` - Returns a list of the node's terminated contracts
* GET `memberApi/CDMResets` - Returns a filtered list of the node's resets. Takes the following querystring params:
    * `contractId`
    * `contractIdScheme`
    * `issuer`
    * `partyReference`

* GET `memberApi/CDMPayments` - Returns a filtered list of the node's payments. Takes the following querystring params:
    * `contractId`
    * `contractIdScheme`
    * `issuer`
    * `partyReference`

* POST `memberApi/requestMembership` - Allows a node to request business network membership. Takes the following body 
  params:
    * `membershipDefinitionJson` (see `integration-tests/src/test/resources/testData/network-definition.json` for an example)
  
* GET `memberApi/me` - Returns the name of the node
* GET `memberApi/members` - Returns a list of all the parties on the network
* GET `memberApi/clients` - Returns a list of the clients on the network
* GET `memberApi/dealers` - Returns a list of the dealers on the network
* GET `memberApi/ccps` - Returns a list of the central counterparties (CCPs) on the network

## The CDM data model

* The CDM defines a number of key classes:
    * A `Contract` class that represents post-execution trades
    * An `Event` class that represents events that affect these trades
    * An `Payment` class that represents payment between two parties
    * An `Observation` class that represents an observation of some fact (e.g. interest rate)
    * A `Reset` class that represents the reset of a floating swap leg (would typically refer to an observation event 
      in its lineage)
    
* Each `Event` has two key fields:
    * `primitive`, defining the type of the event and the changes it causes
    * `lineage`, defining which contracts and other events are related to this event

* There are seven primitive event types, which can live in an `Event` object on their own or together with other 
  primitive events, thus creating more complex events:
    * `Allocation`
    * `Exercise`
    * `NewTrade`
    * `Observation`
    * `Payment`
    * `QuantityChange`
    * `Reset`
    * `TermsChange`

## The Corda CDM libraries

The Corda CDM libraries provide utilities for adding and retrieving CDM events from the ledger, alongside other 
functionality.

### Mapping the CDM data model to Corda

* Each CDM event becomes a Corda transaction

* Each CDM primitive event becomes a command of this transaction

* Any product of the CDM event (e.g. contract, payment, observation...) become an output state of this transaction

* Any contract referenced by the `before` clause of any of the primitive events (e.g. `quantityChange`) is expected to 
  be already stored on the ledger and will become an input state of this transaction

* Any contract or event referenced by the `lineage` clause becomes a reference state of this transaction

* The required signers for this transaction is the set of all the parties listed in the contracts, payments, 
  observations, etc. of this CDM event
  * Multiple CDM parties can map to same Corda Party. The mapping is maintained in simple wrapped map called 
    `NetworkMap`

* Metadata about the CDM event becomes an `EventMetadata` output state of this transaction

### Writing events to the ledger

When writing a CDM event to the ledger:

* `parseEventFromJson` allows you to convert a JSON representation of an event into an `Event` object
* `CdmTransactionBuilder` takes this `Event` object and builds a transaction containing:
    * Input and output states corresponding to the primitives in the `Event` object
    * Reference states corresponding to the "lineage" of the `Event`
    * An output `EventMetadataState` state that serves to embed the CDM event ID in the transaction
    
* You can then sign and commit the transaction as normal

You can see an example of this in 
`cordapp/src/main/kotlin/net/corda/derivativestradingnetwork/flow/PersistCDMEventOnLedgerFlow`.

### Querying the vault

The `DefaultCdmVaultQuery` utility class exposes methods to retrieve specific types of CDM events from the ledger:

* `getLiveContracts`
* `getTerminatedContracts`
* `getNovatedContracts`
* `getResets`
* `getPayments`

### Mapping partyIds to nodes

Each node is defined by the following attributes:

   * `legalEntityId`
   * `type`
   * `name`
   * list of (`partyId`, `account`) pairs
   
These attributes are used to map the `partyId`s contained in CDM events to specific Corda nodes.

## Examples

`integration-tests/src/test/kotlin/net/corda/derivativestraditingnetwork/integrationTests/EndToEndTest.kt` sets up a 
network of dealers, clients and ccp, governed by a Business Network Operator. Please refer to this test suite to see:

* How membership in the business network is requested and granted
* How CDM events are stored by nodes on the ledger
* How CDM events are queried from the ledger