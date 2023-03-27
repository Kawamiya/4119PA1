Zijian Xu

zx2413

CS4119 PA1

TO RUN MY CODE:
1. To run my server, please type "java -jar UDPChat.jar -s <server_port>"
2. To run a client, please type "java -jar UDPChat.jar -c <name> <server-ip> <server-port> <client-port>"

NOTES

	I use 2 extra dependencies to help me reduce simple and repetitive work:
        lombok: quickly generate set, get and constructor methods for a class.
        fastJSON: parse Object to JSON String or parse JSON String to Object.(I don't know why Java do not have a basic packet just like json in python to deal with such works)

    And because of these 2 dependencies, it's difficult to use javac to compile and generate jar file. 
    So I didn't write makefile and use maven plugin to generate a jar with dependencies. Hope this doesn't violate the requirement.
