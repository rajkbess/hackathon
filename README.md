![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# Derivatives Trading Network

## Pre-Requisites

   * This project uses Corda 4.0 to take advantage of reference states
   * As a consequence this project uses Kotlin 1.2

## Description

This project sets up a network of clients, dealers and central counterparties that can trade together using CDM events. This business network is governed by Business Network Operator, which is also part of the project.

Each node is defined by 

   * legalEntityId
   * type
   * name
   * list of (partyId, account) pairs
   
This definition is then used to map `partyId` on a CDM event to a Corda party