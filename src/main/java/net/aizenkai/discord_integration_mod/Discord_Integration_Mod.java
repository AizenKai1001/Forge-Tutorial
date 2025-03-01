package net.aizenkai.discord_integration_mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.server.FMLServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.util.Optional;

import static net.minecraft.command.Commands.literal;

@Mod(Discord_Integration_Mod.MOD_ID)
public class Discord_Integration_Mod {

    public static final String MOD_ID = "discord_integration_mod";
    private static final Logger LOGGER = LogManager.getLogger();
    private static DiscordBotConnection botConnection;

    public Discord_Integration_Mod(FMLJavaModLoadingContext bus) {

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // Register Config
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);

        MinecraftForge.EVENT_BUS.register(this);

    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        LOGGER.info("HELLO FROM CLIENT SETUP");

        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            botConnection = new DiscordBotConnection();
            botConnection.start();
            return null;
        });
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSource> dispatcher = event.getDispatcher();
        dispatcher.register(literal("testcommand")
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal("This is a test command!"), false);
                    return 1;
                }));
    }

    @SubscribeEvent
    public void onChatMessage(ClientChatReceivedEvent event) {
        if (botConnection != null && botConnection.isConnected()) {
            Component message = event.getMessage();
            String text = message.getString();
            botConnection.sendMessage(text);
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Config {
        public static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
        public static final ForgeConfigSpec CLIENT_SPEC;

        public static final ForgeConfigSpec.ConfigValue<String> botIpAddress;
        public static final ForgeConfigSpec.ConfigValue<Integer> botPort;

        static {
            CLIENT_BUILDER.comment("Settings for the Discord Integration Mod");
            botIpAddress = CLIENT_BUILDER.define("botIpAddress", "127.0.0.1");
            botPort = CLIENT_BUILDER.defineInRange("botPort", 12345, 1024, 65535);
            CLIENT_SPEC = CLIENT_BUILDER.build();
        }
    }

    public static class DiscordBotConnection extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private boolean isConnected = false;

        public void run() {
            String ipAddress = Config.botIpAddress.get();
            int port = Config.botPort.get();

            try {
                socket = new Socket(ipAddress, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                isConnected = true;
                LOGGER.info("[DiscordChatMod] Connected to Discord Bot: " + ipAddress + ":" + port);

                // Start a thread to listen for messages from the bot
                new Thread(this::listenForMessages).start();
            } catch (IOException e) {
                LOGGER.error("[DiscordChatMod] Could not connect to Discord Bot: " + e.getMessage());
                isConnected = false;
            }
        }

        public void sendMessage(String message) {
            if (isConnected && out != null) {
                out.println(Minecraft.getInstance().player.getDisplayName().getString() + ": " + message);
            }
        }

        private void listenForMessages() {
            String message;
            try {
                while ((message = in.readLine()) != null) {
                    // Handle message received from the bot.  Make sure it's on the main thread.
                    String finalMessage = message;
                    Minecraft.getInstance().ingameGUI.getChatGUI().addChatMessage(Component.literal("[Discord] " + finalMessage));
                }
            } catch (IOException e) {
                LOGGER.error("[DiscordChatMod] Error receiving message from bot: " + e.getMessage());
                disconnect();
            } finally {
                disconnect();
            }
        }

        public boolean isConnected() {
            return isConnected;
        }

        public void disconnect() {
            isConnected = false;
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                LOGGER.error("[DiscordChatMod] Error closing socket: " + e.getMessage());
            } finally {
                out = null;
                in = null;
                socket = null;
            }
        }
    }
}