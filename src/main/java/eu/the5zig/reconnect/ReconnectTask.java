package eu.the5zig.reconnect;

import com.google.common.base.Strings;
import eu.the5zig.reconnect.net.BasicChannelInitializer;
import eu.the5zig.reconnect.util.Utils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.util.internal.PlatformDependent;
import net.md_5.bungee.BungeeServerInfo;
import net.md_5.bungee.ServerConnection;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.Title;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerConnectEvent.Reason;
import net.md_5.bungee.netty.PipelineUtils;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ReconnectTask {

	private static final TextComponent EMPTY = new TextComponent("");

	private final Reconnect instance;
	private final ProxyServer bungee;
	private final UserConnection user;
	private final ServerConnection server;
	private final BungeeServerInfo target;
	private final long startTime;

	private int visualTries;
	private int tries;

	public ReconnectTask(Reconnect instance, ProxyServer bungee, UserConnection user, ServerConnection server, long startTime) {
		this.instance = instance;
		this.bungee = bungee;
		this.user = user;
		this.server = server;
		this.target = server.getInfo();
		this.startTime = startTime;
	}

	/**
	 * Tries to reconnect the User to the specified Server. In case that fails, this method will be executed again
	 * after a short timeout.
	 */
	public void tryReconnect() {
		if (tries + 1 > instance.getMaxReconnectTries()) {
			// If we have reached the maximum reconnect limit, proceed BungeeCord-like.
			instance.cancelReconnectTask(user.getUniqueId());

			ServerInfo def = user.updateAndGetNextServer(server.getInfo());
			if (target != def) {
				// If the fallback-server is not the same server we tried to reconnect to, send the user to that one instead.
				server.setObsolete(true);
				user.connectNow(def, Reason.SERVER_DOWN_REDIRECT);
				user.sendMessage(bungee.getTranslation("server_went_down"));

				// Send fancy title if it's enabled in config, otherwise reset the connecting title.
				if (!instance.getFailedSubtitle().isEmpty())
					user.sendTitle(createFailedTitle());
				else
					user.sendTitle(ProxyServer.getInstance().createTitle().reset());

				// Send fancy action bar message if it's enabled in config, otherwise reset the connecting action bar message.
				if (!instance.getFailedActionBar().isEmpty())
					sendFailedActionBar(user);
				else
					user.sendMessage(ChatMessageType.ACTION_BAR, EMPTY);
			} else {
				// Otherwise, disconnect the user with a "Lost Connection"-message.
				user.disconnect(bungee.getTranslation("lost_connection"));
			}
			return;
		}

		// If we are already connecting to a server, cancel the reconnect task.
		if (user.getPendingConnects().contains(target)) {
			instance.getLogger().warning("User already connecting to " + target);
			return;
		}

		// Add pending connection.
		user.getPendingConnects().add(target);

		// Add a try if the delay is not active
		if (startTime + instance.getDelayBeforeTrying() <= System.currentTimeMillis()) {
			tries++;
		}
		visualTries++;

		// Send fancy Title
		if (!instance.getReconnectingSubtitle().isEmpty()) {
			createReconnectTitle().send(user);
		}

		// Send fancy Action Bar Message
		if (!instance.getReconnectingActionBar().isEmpty()) {
			sendReconnectActionBar(user);
		}

		// Establish connection to the server.
		ChannelInitializer<Channel> initializer = new BasicChannelInitializer(bungee, user, target);
		ChannelFutureListener listener = new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()
						&& startTime + instance.getDelayBeforeTrying() <= System.currentTimeMillis()) {
					// If reconnected successfully, remove from map and send another fancy title.
					instance.cancelReconnectTask(user.getUniqueId());

					// Send fancy Title
					if (!instance.getConnectingSubtitle().isEmpty()) {
						createConnectingTitle().send(user);
					}

					// Send fancy Action Bar Message
					if (!instance.getConnectingActionBar().isEmpty()) {
						sendConnectActionBar(user);
					}
				} else {
					future.channel().close();
					user.getPendingConnects().remove(target);

					// Send KeepAlive Packet so that the client won't time out.
					//user.unsafe().sendPacket(new KeepAlive(RANDOM.nextInt())); - >KeepAlive in 1.12.2 - krusic22

					// Schedule next reconnect.
					Utils.scheduleAsync(instance, new Runnable() {
						@Override
						public void run() {
							// Only retry to reconnect the user if he is still online and hasn't been moved to another server.
							if (instance.isUserOnline(user) && Objects.equals(user.getServer(), server)) {
								tryReconnect();
							} else {
								instance.cancelReconnectTask(user.getUniqueId());
							}
						}
					}, instance.getReconnectMillis(), TimeUnit.MILLISECONDS);
				}
			}
		};

		// Create a new Netty Bootstrap that contains the ChannelInitializer and the ChannelFutureListener.
		Bootstrap b = new Bootstrap().channel(PipelineUtils.getChannel(target.getAddress())).group(server.getCh().getHandle().eventLoop()).handler(initializer).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, instance.getReconnectTimeout()).remoteAddress(target.getAddress());

		// Windows is bugged, multi homed users will just have to live with random connecting IPs
		if (user.getPendingConnection().getListener().isSetLocalAddress() && !PlatformDependent.isWindows( )&& user.getPendingConnection().getListener().getSocketAddress() instanceof InetSocketAddress) {
			b.localAddress(user.getPendingConnection().getListener().getHost().getHostString(), 0);
		}
		b.connect().addListener(listener);
	}

	/**
	 * Creates a Title containing the reconnect-text.
	 *
	 * @return a Title that can be send to the player.
	 */
	private Title createReconnectTitle() {
		Title title = ProxyServer.getInstance().createTitle();
		title.title(new TextComponent(instance.getReconnectingTitle().replace("{%dots%}", getDots())));
		title.subTitle(new TextComponent(instance.getReconnectingSubtitle().replace("{%dots%}", getDots())));
		// Stay at least as long as the longest possible connect-time can be.
		title.stay((instance.getReconnectMillis() + instance.getReconnectTimeout() + 1000) / 1000 * 20);
		title.fadeIn(0);
		title.fadeOut(0);

		return title;
	}

	/**
	 * Sends an Action Bar Message containing the reconnect-text to the player.
	 */
	private void sendReconnectActionBar(UserConnection user) {
		user.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(instance.getReconnectingActionBar().replace("{%dots%}", getDots())));
	}

	/**
	 * Creates a Title containing the connecting-text.
	 *
	 * @return a Title that can be send to the player.
	 */
	private Title createConnectingTitle() {
		Title title = ProxyServer.getInstance().createTitle();
		title.title(new TextComponent(instance.getConnectingTitle().replace("{%dots%}", getDots())));
		title.subTitle(new TextComponent(instance.getConnectingSubtitle()));
		title.stay(20);
		title.fadeIn(10);
		title.fadeOut(10);

		return title;
	}

	/**
	 * Sends an Action Bar Message containing the connect-text to the player.
	 */
	private void sendConnectActionBar(UserConnection user) {
		user.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(instance.getConnectingActionBar()));
	}

	/**
	 * Created a Title containing the failed-text.
	 *
	 * @return a Title that can be send to the player.
	 */
	private Title createFailedTitle() {
		Title title = ProxyServer.getInstance().createTitle();
		title.title(new TextComponent(instance.getFailedTitle().replace("{%dots%}", getDots())));
		title.subTitle(new TextComponent(instance.getFailedSubtitle()));
		title.stay(80);
		title.fadeIn(10);
		title.fadeOut(10);

		return title;
	}

	/**
	 * Sends an Action Bar Message containing the failed-text to the player.
	 */
	private void sendFailedActionBar(final UserConnection user) {
		user.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(instance.getFailedActionBar()));

		// Send an empty action bar message after 5 seconds to make it disappear again.
		bungee.getScheduler().schedule(instance, new Runnable() {
			@Override
			public void run() {
				user.sendMessage(ChatMessageType.ACTION_BAR, EMPTY);
			}
		}, 5L, TimeUnit.SECONDS);
	}

	/**
	 * @return a String that is made of dots for the "dots animation".
	 */
	private String getDots() {
		String dots = "";

		for (int i = 0, max = visualTries % 4; i < max; i++) {
			dots += ".";
		}

		return dots;
	}

	/**
	 * Resets the title and action bar message if the player is still online
	 */
	public void cancel() {
		if (instance.isUserOnline(user)) {
			if (!Strings.isNullOrEmpty(instance.getReconnectingSubtitle()) || !Strings.isNullOrEmpty(instance.getConnectingSubtitle())) {
				// For some reason, we have to reset and clear the title, so it completely disappears -> BungeeCord bug?
				bungee.createTitle().reset().clear().send(user);
			}
			if (!Strings.isNullOrEmpty(instance.getConnectingActionBar())) {
				user.sendMessage(ChatMessageType.ACTION_BAR, EMPTY);
			}
		}
	}

}
