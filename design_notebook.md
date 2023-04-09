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