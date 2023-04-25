## Overview

For this project, we implemented the distributed random walk algorithm from the paper `Distributed Random Walks` by Das Sarma, Nanongkai, Pandurangan, and  Tetali. Our goal is to understand the algorithm more deeply through implementing it, while also understanding the concerns and considerations taht may arise in an implementation of such an algorithm. This algorithm is also structured within the standard CONGEST communication model for distributed systems. This is a largely theoretical model, and so we are interested to see what considerations arise as we use this model in an implementation.

Our code is structured as follows. First, the input graph is also the structure of the network: it determines which machines the network will have and also what the neighbors (connections) in the network. There is an `AgentController`, which starts each of the various machines. Each node of the graph should be viewed (in the algorithm and the implementation) as its own machine in the distributed system. The `AgentRunner` contains the methods specific to each of the machines/nodes, and each node of the graph should be viewed as its own `AgentRunner`. Nodes can only communicate with their neighbors in the graph.

We have also implemented visuals, which include color schemes to demonstrate which of the subroutines in the distributed random walk algorithm is happening at each of the nodes. This therefore reflects how the algorithm operates in a distributed fashion across the various nodes of the network.

## Design Decisions

We have decided to use grpc for this project, as it's streamlined our code for past projects and is easy to use.

Overall, our design decisions reflected the structure and steps of the distributed random walks algorithm from the paper.

One design decision that diverged from the algorithm was in the send-more-coupons algorithm, Part 1. In the theoretical algorithm, it states tat neigbors of a node should send the ID of the node v and the number of coupons for which z is picked as a reciever, denoted by c(u, v). When z receives messages, he reconstructs c(u, v) corresponding messages. However, we found that the number c(u, v) could be inferred by the number of messages that were sent to z, without the need to compress the c(u, v) messages into one and then un-compress them. We do not think there is a reason to send c(u, v) at the level of the implementation.

Another design decision arose around constructing message request and message reply objects with grpc. In our messageService proto, we chose to create message requests and replies of the form: generic message request/reply, bfs message request/reply, coupon message request/reply. We found that having different request and reply message objects for each of these categories avoided confusion and allowed us to tailor the messages being sent for the various tasks in the algorithm.

## Log
### April 9th
Implemented barebones service which is able to send messages throughout a graph. To do this,
we implemented a `AgentController`, which starts up threads corresponding to each of the nodes.
Each node (which is a `AgentRunner` object) can send and receive messages across its adjacent
edges.

>**Visualization**
> In order to allow visualization of the system, we implemented an additional service, called
> `Log`. Each node, after sending and receiving confirmation of a message, will log the call
> to the `AgentController`. The `AgentController` can then visualize / collect statistics
> about the system.

```protobuf
service Log {
  rpc SendLog (MessageLog) returns (StatusReply) {}
}

message MessageLog {
  int32 sending_node = 1;
  int32 receiving_node = 2;
}
message StatusReply {
  bool success = 1;
}
```