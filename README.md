# Project Goal
The goal of this project was to implement a Chat server and Client in java that sends messages over TCP. The server is mainly focused on chat rooms a client first joins a room than can send and receive messages to and from all the room's particpants, although it is possible to send private messages through a command
# Message format
All messages sent terminate in newlines and messages can´t contain newlines, therefore every newline is the end of a message, a message may span multiple tcp packets and a tcp packet may contain more than one message, consequently it is the job of the server to delineate messages, by buffering or splitting packets 

Messages sent by the client can be of two forms:
 - Commands: Starts with '/' and may be sent at anytime
 - Regular messages: Can only be sent while the client is connected to a Chat room and will be forwarded by the server to every client connected to that chat room

Messages sent by the Server can be of three forms:
  - Message Reply: Sent only to the client who sent the original message 
    - OK: Command used successfully
    - ERROR: Error in command used or message sent
    - BYE: Sent when user uses ``\bye`` to disconnect  
  - Announcement: Announces to every other person in the chat room the that someone joined or left said chat
  - Forwarded message: Forwards message sent by client to everyone in the clients chat room
## List of commands
  - ``\nick name``: Sets username
  - ``\join room``: Used to join a Chatroom
  - ``\leave``: Used to leave current chat room
  - ``\bye``: Used to disconnect from the server
  - ``\priv name message``: Sends *message* privately to user named *name*
# Usage
To use the server compile it with ``javac`` then run it with the command:
```
java ChatServer {port}
```
To use the client it with ``javac`` then run it with the command:
```
java ChatClient {server address} {server port}
```
