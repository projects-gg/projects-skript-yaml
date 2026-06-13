package me.sashie.skriptyaml;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import ch.njol.skript.effects.Delay;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import ch.njol.skript.lang.TriggerItem;

/**
 * Effects that extend this class are ran asynchronously. Next trigger item will
 * be ran in main server thread, as if there had been a delay before.
 * <p>
 * Majority of Skript and Minecraft APIs are not thread-safe, so be careful.
 */
public abstract class AsyncEffect extends Delay {

	private static final ReentrantLock SKRIPT_EXECUTION = new ReentrantLock(true);
	private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());
	private static ExecutorService threads = createExecutor();

	private static ExecutorService createExecutor() {
		return Executors.newFixedThreadPool(THREAD_COUNT, new ThreadFactory() {
			private final AtomicInteger threadId = new AtomicInteger();

			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, "skript-yaml-async-" + threadId.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			}
		});
	}

	private static synchronized ExecutorService getExecutor() {
		if (threads.isShutdown() || threads.isTerminated())
			threads = createExecutor();
		return threads;
	}

	public static synchronized void shutdownExecutor() {
		threads.shutdownNow();
		try {
			if (!threads.awaitTermination(2, TimeUnit.SECONDS))
				SkriptYaml.warn("Timed out while stopping async tasks");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	@Override
	@Nullable
	protected TriggerItem walk(Event e) {
		debug(e, true);
		SkriptYaml.getInstance().getSkriptAdapter().addDelayedEvent(e);
		CompletableFuture<Void> run = CompletableFuture.runAsync(new Runnable() {
			public void run() {
				execute(e);
			}
		}, getExecutor());
		run.whenComplete((r, err) -> {
			if (err != null)
				err.printStackTrace();

			SkriptYaml plugin;
			try {
				plugin = SkriptYaml.getInstance();
			} catch (IllegalStateException ignored) {
				return;
			}
			if (!plugin.isEnabled())
				return;

			Bukkit.getScheduler().runTask(plugin, new Runnable() {
				@Override
				public void run() {
					SKRIPT_EXECUTION.lock();
					try {
						if (getNext() != null) {
							walk(getNext(), e);
						}
					} finally {
						SKRIPT_EXECUTION.unlock();
					}
				}
			});
		});
		return null;
	}
}
