# Laminar components

Experiments with building components for common tasks in way that is idiomatic to both Scala and Laminar.

You can run a full-stack application that incorporates the components listed by doing

```
sbt> ~runExampleDev
```

and visiting http://localhost:9000/frontend/


## Laminar Websocket

The main purpose is to establish a typesafe protocol for a websocket connection with a server (usually part of a shared compilation module, used by both server and client).

[Documentation](docs/LaminarWebsocket.md)
