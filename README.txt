## Overview

My program is designed with a simple Server/Client model. The server and client classes both have
their main threads, threads to handle the heartbeats, and threads to handle incoming communication
with the other entity (client/server). My application sends all of its messages using a similar Message
class structure, which has integer types. This makes sure that I can test what types of messages
I am reading in which helps for debugging and for dealing with the various functionalities of the program.

## Running
To run my code, enter the eco2116_Java directory and type "make" to compile.
On one terminal, enter:
java Server <PortNumber>

This begins the server.

To begin muliple clients (as many as you want), enter in other terminal windows:
java Client <ServerIP> <PortNumber>

From there, the various commands are as described in the assignment. I attempted to implement
privacy and consent features, but bugs still exist. In order to test that feature, when user one requests the address of
user two with getaddress, user two must "request" user one. This is done with the "request <username>"
command. This allows the P2P connection to initiate. In order to end this request, the command
is "endrequest <username>".
