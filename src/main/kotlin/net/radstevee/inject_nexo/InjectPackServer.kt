package net.radstevee.inject_nexo

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.configs.Settings
import com.nexomc.nexo.pack.server.NexoPackServer
import com.nexomc.nexo.utils.AdventureUtils.LEGACY_SERIALIZER
import com.nexomc.nexo.utils.VersionUtil
import com.nexomc.nexo.utils.logs.Logs
import io.netty.channel.ChannelHandlerContext
import net.kyori.adventure.resource.ResourcePackInfo.resourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest.resourcePackRequest
import net.mcbrawls.inject.api.InjectorContext
import net.mcbrawls.inject.http.HttpByteBuf
import net.mcbrawls.inject.http.HttpByteBuf.httpBuf
import net.mcbrawls.inject.http.HttpInjector
import net.mcbrawls.inject.http.HttpRequest
import net.mcbrawls.inject.spigot.InjectSpigot
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import team.unnamed.creative.BuiltResourcePack
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.util.UUID

/** A resource pack server using inject */
public class InjectPackServer : NexoPackServer, HttpInjector() {
    /** The public address of the server */
    private val publicAddress: String = publicAddress()
    /** The built resource pack */
    private val builtPack: BuiltResourcePack by lazy {
        NexoPlugin.instance().packGenerator().builtPack()!!
    }
    /** The contents of the resource pack, in bytes */
    private val builtPackBytes: ByteArray by lazy { builtPack.data().toByteArray() }
    /** The hash of this resource pack, represented as a 20-char string */
    private val builtPackHash: String by lazy { builtPack.hash() }

    private companion object {
        /** Retrieves the public address of this server from AWS checkip */
        private fun publicAddress(): String {
            val urlString = "http://checkip.amazonaws.com/"
            val publicAddress = runCatching {
                val url = URI.create(urlString).toURL()
                BufferedReader(InputStreamReader(url.openStream())).use(BufferedReader::readLine)
            }
                .onFailure { exception ->
                    if (Settings.DEBUG.toBool()) {
                        exception.printStackTrace()
                    }
                    Logs.logError("Failed to get publicAddress for INJECT server...")
                    Logs.logWarn("You can manually set it in `settings.yml` at ...")
                }
                .getOrElse { "0.0.0.0" }

            return Settings.SELFHOST_PUBLIC_ADDRESS.toString(publicAddress) // Probably want to make a separate setting for this
        }
    }

    override fun packUrl(): String {
        // You might want to add a setting to specify the server port
        val serverPort = Bukkit.getPort()

        @Suppress("HttpUrlsUsage")
        return "http://$publicAddress:$serverPort/$builtPackHash.zip"
    }

    override fun sendPack(player: Player) {
        NexoPlugin.instance().packGenerator().packGenFuture?.thenRun {
            val hashArray = NexoPackServer.hashArray(this.builtPackHash)
            val url = this.packUrl()
            val packUUID = UUID.nameUUIDFromBytes(hashArray)

            val request = resourcePackRequest()
                .required(NexoPackServer.mandatory)
                .replace(true)
                .prompt(NexoPackServer.prompt)
                .packs(resourcePackInfo(packUUID, URI.create(url), this.builtPackHash)).build()

            if (VersionUtil.isPaperServer) {
                player.sendResourcePacks(request)
            } else {
                @Suppress("DEPRECATION") // Spigot should be deprecated as a whole
                player.setResourcePack(packUUID, url, hashArray, LEGACY_SERIALIZER.serialize(NexoPackServer.prompt), NexoPackServer.mandatory)
            }
        }
    }

    override fun isRelevant(ctx: InjectorContext, request: HttpRequest): Boolean {
        return request.requestURI == "/$builtPackHash.zip"
    }

    override fun intercept(ctx: ChannelHandlerContext, request: HttpRequest): HttpByteBuf? {
        val buf = httpBuf(ctx)
        buf.writeStatusLine("1.1", 200, "OK")

        buf.writeHeader("Content-Type", "application/zip")
        buf.writeHeader("Content-Length", this.builtPackBytes.size.toString())
        buf.writeBytes(this.builtPackBytes)

        return buf
    }

    override fun start() {
        InjectSpigot.INSTANCE.registerInjector(this)
        super.start()
    }
}
