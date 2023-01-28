package bot

import bot.database.experience.Experiences
import bot.database.meme.Memes
import bot.database.rating.RatingRateLimits
import bot.database.rep_messages.RepMessages
import bot.database.tag.Tags
import bot.database.user.Users
import bot.extensions.*
import bot.lib.Config
import bot.lib.ConfigDto
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.i18n.SupportedLocales
import com.typesafe.config.ConfigRenderOptions
import dev.kord.common.annotation.KordVoice
import dev.kord.core.kordLogger
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import io.github.config4k.toConfig
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import org.jetbrains.exposed.sql.Database as KtDatabase

@KordVoice
@ExperimentalTime
@PrivilegedIntent
suspend fun main() {
	val configPath = Config.path.toPath()

	if (!FileSystem.SYSTEM.exists(configPath)) {
		kordLogger.warn("Config not found, creating...")

		val renderOptions = ConfigRenderOptions.defaults()
			.setJson(false)
			.setOriginComments(false)

		FileSystem.SYSTEM.write(configPath, true) {
			writeUtf8(
				ConfigDto.Discord().toConfig("discord")
					.root().render(renderOptions)
			)

			writeUtf8("\n")

			writeUtf8(
				ConfigDto.Database().toConfig("database")
					.root().render(renderOptions)
			)
		}

		kordLogger.warn("Configure the config!")
		exitProcess(1)
	}

	Config.update()

	val dbConfig = Config.database

	KtDatabase.connect(
		url = "jdbc:pgsql://${dbConfig.url}/${dbConfig.database}",
		user = dbConfig.username,
		password = dbConfig.password,
		driver = "com.impossibl.postgres.jdbc.PGDriver"
	)

	transaction {
		SchemaUtils.create(
			Users,
			RatingRateLimits,
			Experiences,
			Memes,
			Tags,
			RepMessages
		)
	}

	val discordConfig = Config.discord
	val bot = ExtensibleBot(discordConfig.token) {
		intents {
			+ Intents.nonPrivileged
			+ Intent.MessageContent
		}

		i18n {
			defaultLocale = SupportedLocales.ALL_LOCALES[discordConfig.language] ?: SupportedLocales.ENGLISH

			interactionUserLocaleResolver()
		}

		presence {
			val version = javaClass.classLoader.getResource("version.txt")!!.readText()
			playing("${discordConfig.game} | $version")
		}

		chatCommands {
			enabled = true
			prefix { "~" }
		}

		applicationCommands {
			defaultGuild(discordConfig.guildId)
		}

		extensions {
			if (discordConfig.sentryLink.isNotEmpty())
				sentry {
					dsn = discordConfig.sentryLink
				}

			add(::PingCommand)
			add(::Reminder)
			add(::Votes)
			add(::Scripting)
			add(::KoinManage)
			add(::Experience)
			add(::Fun)
			add(::Music)
			add(::Info)
			add(::Tuts)
			add(::StatsReport)
			add(::MemeScore)
			add(::SocialRating)
			add(::Tags)
			add(::PrivateRooms)
			add(::Statistic)
			add(::ReloadExtension)
		}
	}

	Config.loadDisabledExtensions()
	Config.disabledExtensions.forEach { extensionName ->
		bot.unloadExtension(extensionName)
	}

	bot.start()
}