# Bungee-Reconnect
A simple BungeeCord-Plugin that automatically tries to reconnect all players whenever a server restarts.

BUILD
------
git clone https://github.com/Letsmine/Bungee-Reconnect.git <br>
cd Bungee-Reconnect <br>
wget http://nexus.codecrafter47.dyndns.eu/repository/snapshots/net/md-5/bungeecord-bootstrap/1.16-R0.4-SNAPSHOT/bungeecord-bootstrap-1.16-R0.4-20201104.091113-11.jar <br>
mvn install <br>
*The jar file will be inside the targer directory*

------
Installation & Configuration

Bungee-Reconnect is a **BungeeCord plugin**! Just put it into the BungeeCord plugins folder, start/restart your server and the plugin will generate a configuration in the plugins folder.
Addition configs are available thanks to Terrobility.

Features
------
- Reconnect all players when a server restarts - you don't need an additional limbo server.
- Move the affected players to the fallback server or kick them from the proxy, if the server doesn't start within a specific time period again.
- Send titles and action bar messages (+ animated dots variable) to the players while they're waiting for the reconnect or if they got moved to the fallback server.
- Configure servers that are to be ignored by this plugin, custom messages, maximum amount of retries and many other things!
- Compatible with other BungeeCord plugins - its behavior isn't supposed to break anything at all!
- Provides an API for plugin developers.


API
------
Bungee-Reconnect provides a [**ServerReconnectEvent**](src/eu/the5zig/reconnect/api/ServerReconnectEvent.java).
If you're a developer and want to cancel the reconnect-process for any reason, you can just cancel this event.


Other
------
Officially tested with **Waterfall #384** and **Paper 1.16.4-R0.1-SNAPSHOT #288**.
This plugin supports (officially) **BungeeCord 1.16** and **Spigot 1.16.4**!
If you encounter any problems, please first make sure you update BungeeCord and Spigot before creating an issue report.
Under certain circumstances, this plugin might work with older versions, but we <b>don't</b> support it officially.


Useful Links
------
- [BungeeCord](https://www.spigotmc.org/wiki/bungeecord/)
- [Plugin Page](https://www.spigotmc.org/resources/bungee-reconnect.16429/)
- [Inspired by a SpigotMC Forum Thread](https://www.spigotmc.org/threads/restart-plugin-built-into-spigot-pls-help-4-diamond.111789/)


Credits
------
- Development: [5zig](https://github.com/5zig)
- Contribution: [Krymonota](https://github.com/Krymonota), [Terrobility](https://github.com/Terrobility), [krusic22](https://github.com/krusic22)
- Inspiration: [SpigotMC Forum Thread](https://www.spigotmc.org/threads/restart-plugin-built-into-spigot-pls-help-4-diamond.111789/)
