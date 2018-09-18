![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Derivatives Trading Network

## Description

This project allows you to easily spin up a network of client, dealer and central counterparty nodes that can trade 
together using CDM events. This business network is governed by a Business Network Operator.

Each node is defined by the following attributes:

   * `legalEntityId`
   * `type`
   * `name`
   * list of (`partyId`, `account`) pairs
   
These attributes are used to map the `partyId`s contained in CDM events to specific Corda nodes.

Out of the box, each node is running Corda 4 and has the following installed:

* The Corda CDM libraries
* A `PersistCDMEventOnLedgerFlow` to persist CDM events to the ledger
* A web API for interacting with the ledger (see `Interacting with the nodes`, below)

## Running the network

There are three ways of setting up the network:

* Locally, using the Node Driver
* Locally, using `deployNodes` + `runNodes`
* In the cloud

It is recommended to use the first option because it is the fastest, the least resource-intensive, it allows you to 
debug into the running nodes during development, and it automates the process of adding the nodes to the business 
network.

Because the network is large (10 nodes), the second option may fail with an out-of-memory error.

### Locally, using the Node Driver

Run the `Run Network` run configuration from IntelliJ.

### Locally, using deployNodes + runNodes

Follow the instructions here: 
https://docs.corda.net/tutorial-cordapp.html#running-the-example-cordapp-from-the-terminal. In summary, on Mac:

* `./gradlew clean deployNodes`
* ``build/nodes/runnodes``

And on Windows:

* `gradlew clean deployNodes`
* ``build\nodes\runnodes``

Nodes need to request membership, which is then approved by the BNO. To quickly run these steps for all nodes:

```bash
cd deploy 
./joinNetwork.sh 
```

### In the cloud

A process for quickly deploying to a cloud server is documented in the 'deploy' folder, simply refer to 
the README. Alternatively try editing 'build.gradle' to remove some of the nodes (_will need to rerun 
the steps above afterwards_).  

As when using `deployNodes` + `runNodes`, the nodes need to request membership, which is then approved by the BNO. To 
quickly run these steps for all nodes:
                    
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

## Sample usage

`integration-tests/src/test/kotlin/net/corda/derivativestraditingnetwork/integrationTests/EndToEndTest.kt` sets up a 
network of dealers, clients and ccp, governed by a Business Network Operator. Please refer to this test suite to see:

* How membership in the business network is requested and granted
* How CDM events are stored by nodes on the ledger
* How CDM events are queried from the ledger

## Extending this CorDapp

If you need to extend this CorDapp, you should do so by adding code in the following locations:

* `cordapp-contracts-states/src/main/java/net/corda/yourcode`, for new states/contracts written in Java
* `cordapp-contracts-states/src/main/kotlin/net/corda/yourcode`, for new states/contracts written in Kotlin
* `cordapp/src/main/java/net/corda/yourcode`, for other new classes written in Java
* `cordapp/src/main/kotlin/net/corda/yourcode`, for other new classes written in Kotlin

## Notes

* This project uses Corda 4.0 to take advantage of reference states
* As a consequence this project uses Kotlin 1.2