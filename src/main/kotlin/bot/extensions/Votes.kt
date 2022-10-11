package bot.extensions

import bot.lib.Utils
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.defaultingString
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.ephemeralButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.i18n.TranslationsProvider
import com.kotlindiscord.kord.extensions.types.edit
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import dev.kord.common.Color
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.rest.builder.message.create.embed
import dev.kord.rest.builder.message.modify.embed
import kotlinx.datetime.Clock
import org.koin.core.component.inject
import kotlin.time.Duration

class Votes : Extension() {
	override val name = "votes"
	override val bundle = "cs_dsbot"

	private val votes = mutableListOf<Vote>()
	val translatorProvider: TranslationsProvider by inject()

	override suspend fun setup() {
		ephemeralSlashCommand(::Args) {
			name = "extensions.votes.commandName"
			description = "extensions.votes.commandDescription"

			action {
				val choices = arguments.choices
					.replace(", ", ",")
					.split(",")

				if (choices.size > 23) {
					respond { content = translate("extensions.votes.tooManyChoices") }
					return@action
				}

				val voteStartTime = Utils.parseTime(Clock.System.now())
				val vote = Vote(arguments.title, choices.map(::Choice), translatorProvider, this@Votes.bundle)
				votes += vote
				val voteIndex = votes.indexOf(vote)

				val voteMessage = channel.createMessage {
					embed {
						title = translate("extensions.votes.inProgress.title")
						description = "__${arguments.title}__\n" + vote.getStatsString()

						footer { text = translate("extensions.votes.startedAt", arrayOf(voteStartTime)) }
					}

					components {
						choices.forEach { label ->
							ephemeralButton {
								bundle = this@Votes.bundle

								this.label = label
								emoji("⬆️")

								action {
									val voteEl = votes[votes.indexOf(vote)]

									voteEl.choices.map { it.votedUsers.remove(user.id) }
									voteEl.choices
										.find { it.name == label }!!
										.votedUsers.add(user.id)

									edit {
										embed {
											title = translate("extensions.votes.inProgress.title")
											description = "__${arguments.title}__\n" + vote.getStatsString()

											footer { text = translate("extensions.votes.startedAt", arrayOf(voteStartTime)) }
										}
									}

									respond { content = translate("extensions.votes.voted") }
								}
							}
						}

						ephemeralButton {
							bundle = this@Votes.bundle

							style = ButtonStyle.Secondary
							label = translate("extensions.votes.seeResults")

							action {
								respond {
									val voteEl = votes[votes.indexOf(vote)]

									content = buildString {
										voteEl.choices.forEach { choice ->
											append("**${choice.name}** ")

											val mentions = choice.votedUsers.map { kord.getUser(it)!!.mention }

											if (mentions.isNotEmpty())
												appendLine(mentions.joinToString(" "))
											else
												appendLine("`${translate("extensions.votes.nobody")}`")
										}
									}
								}
							}
						}
					}
				}

				val duration = Utils.parseDuration(arguments.duration)
				if (duration == null || duration.isNegative() || duration == Duration.ZERO) {
					respond { embed {
						title = translate("extensions.errors.unknownDurationFormat")
						color = Color(0xFF0000)
					} }

					return@action
				}

				vote.task = Scheduler().schedule(duration) {
					val voteEl = votes[voteIndex]

					voteMessage.edit {
						embed {
							title = translate("extensions.votes.ended.title")
							description = buildString {
								appendLine("__${voteEl.title}__")

								voteEl.choices.forEach { choice ->
									appendLine("**${choice.name}** `${choice.votedUsers.size}`")

									val mentionedUsers = choice.votedUsers.map { guild!!.getMember(it).mention }
									if (mentionedUsers.isNotEmpty())
										appendLine(mentionedUsers.joinToString(" "))
									else
										appendLine("`${translate("extensions.votes.nobody")}`")
								}
							}

							footer {
								text = translate("extensions.votes.startedAt", arrayOf(voteStartTime)) + "\n" +
									translate("extensions.votes.endedAt", arrayOf(Utils.parseTime(Clock.System.now())))
							}
						}

						components {}
					}
				}
			}
		}
	}

	inner class Args: Arguments() {
		val title by string {
			name = "title"
			description = translatorProvider.translate("extensions.votes.arguments.title")
		}
		val choices by string {
			name = "choices"
			description = translatorProvider.translate("extensions.votes.arguments.choices")
		}
		val duration by defaultingString {
			name = "duration"
			description = translatorProvider.translate("extensions.votes.arguments.duration")

			defaultValue = "1d"
		}
	}

	data class Vote(
		val title: String,
		val choices: List<Choice>,
		val translationsProvider: TranslationsProvider,
		val bundle: String
	) {
		fun getStatsString() = buildString {
			choices.forEach { choice ->
				appendLine(
					translationsProvider.translate(
						"extensions.votes.votesInfo",
						bundle,
						listOf(choice.name, choice.votedUsers.size)
					)
				)
			}
		}
	}

	data class Choice(
		val name: String,
		val votedUsers: MutableList<Snowflake> = mutableListOf()
	)
}