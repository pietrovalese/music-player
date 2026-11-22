package com.pietro.musicplayer.domain.recommendation

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.pietro.musicplayer.data.db.PlayHistoryEntity
import com.pietro.musicplayer.domain.model.Song
import java.util.Calendar
import kotlin.math.abs

@Immutable
sealed interface WrappedAvailability {
    data object Hidden : WrappedAvailability

    data class Monthly(
        val title: String,
        val periodLabel: String,
        val fromTimestamp: Long,
        val toTimestamp: Long
    ) : WrappedAvailability

    data class Yearly(
        val title: String,
        val periodLabel: String,
        val fromTimestamp: Long,
        val toTimestamp: Long
    ) : WrappedAvailability
}

@Immutable
data class RecommendationSnapshot(
    val recentSongs: List<Song> = emptyList(),
    val topSongs: List<Song> = emptyList(),
    val dailyMixes: List<SmartPlaylist> = emptyList(),
    val madeForYou: List<SmartPlaylist> = emptyList(),
    val becauseYouListened: Pair<String, List<Song>>? = null
)

@Immutable
data class SmartPlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val badgeText: String,
    val accentColor: Long,
    val songs: List<Song>,
    val artworkUri: Uri? = songs.firstOrNull { it.albumArtUri != null }?.albumArtUri
)

@Immutable
data class ArtistStat(
    val name: String,
    val playCount: Int,
    val minutesListened: Long,
    val songCount: Int,
    val topSong: Song?,
    val artworkUri: Uri?
)

@Immutable
data class SongStat(
    val song: Song,
    val playCount: Int,
    val minutesListened: Long
)

@Immutable
data class GenreStat(
    val name: String,
    val playCount: Int,
    val percentage: Float
)

@Immutable
data class WrappedStats(
    val totalMinutes: Long,
    val totalPlays: Int,
    val uniqueSongsExplored: Int,
    val uniqueArtistsExplored: Int,
    val favoriteSongsCount: Int,
    val topArtists: List<ArtistStat>,
    val topSongs: List<SongStat>,
    val topGenres: List<GenreStat>,
    val peakPeriodTitle: String,
    val peakPeriodSubtitle: String,
    val personalityTitle: String,
    val personalityDescription: String
)

object RecommendationEngine {

    private val mixColors = listOf(
        0xFF1ED760L,
        0xFF7358FFL,
        0xFFE8115BL,
        0xFF0D73ECL,
        0xFFBC5900L,
        0xFF148A08L
    )

    private val italianMonths = listOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    )

    private data class UserAffinityProfile(
        val playCountBySongId: Map<Long, Int>,
        val lastPlayedBySongId: Map<Long, Long>,
        val artistScore: Map<String, Double>,
        val genreScore: Map<String, Double>
    )

    fun evaluateWrappedAvailability(nowMs: Long = System.currentTimeMillis()): WrappedAvailability {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Fine anno: 25-31 Dicembre (anno corrente) o 1-7 Gennaio (anno appena concluso)
        if ((month == Calendar.DECEMBER && day >= 25) || (month == Calendar.JANUARY && day <= 7)) {
            val targetYear = if (month == Calendar.JANUARY) year - 1 else year
            val startCal = Calendar.getInstance().apply {
                set(targetYear, Calendar.JANUARY, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                set(targetYear, Calendar.DECEMBER, 31, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return WrappedAvailability.Yearly(
                title = "Il tuo Wrapped $targetYear",
                periodLabel = "Anno $targetYear",
                fromTimestamp = startCal.timeInMillis,
                toTimestamp = endCal.timeInMillis
            )
        }

        // Fine mese (ultimi 3 giorni del mese o primi 5 giorni del mese successivo):
        // mostra il recap del mese precedente completo
        if (day >= maxDay - 2 || day <= 5) {
            val prevCal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val fromMs = prevCal.timeInMillis
            val prevMonthIndex = prevCal.get(Calendar.MONTH)
            val prevYear = prevCal.get(Calendar.YEAR)
            val prevMaxDay = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)

            prevCal.set(Calendar.DAY_OF_MONTH, prevMaxDay)
            prevCal.set(Calendar.HOUR_OF_DAY, 23)
            prevCal.set(Calendar.MINUTE, 59)
            prevCal.set(Calendar.SECOND, 59)
            prevCal.set(Calendar.MILLISECOND, 999)
            val toMs = prevCal.timeInMillis

            val monthName = italianMonths.getOrElse(prevMonthIndex) { "Mese" }
            return WrappedAvailability.Monthly(
                title = "Wrapped di $monthName",
                periodLabel = "$monthName $prevYear",
                fromTimestamp = fromMs,
                toTimestamp = toMs
            )
        }

        return WrappedAvailability.Hidden
    }

    fun emptyWrappedStats(): WrappedStats = WrappedStats(
        totalMinutes = 0L,
        totalPlays = 0,
        uniqueSongsExplored = 0,
        uniqueArtistsExplored = 0,
        favoriteSongsCount = 0,
        topArtists = emptyList(),
        topSongs = emptyList(),
        topGenres = emptyList(),
        peakPeriodTitle = "",
        peakPeriodSubtitle = "",
        personalityTitle = "",
        personalityDescription = ""
    )

    fun computeSnapshot(
        allSongs: List<Song>,
        history: List<PlayHistoryEntity>
    ): RecommendationSnapshot {
        if (allSongs.isEmpty()) return RecommendationSnapshot()

        val now = System.currentTimeMillis()
        val songMap = HashMap<Long, Song>(allSongs.size)
        for (song in allSongs) {
            songMap[song.id] = song
        }

        val profile = buildAffinityProfile(allSongs, songMap, history, now)

        val seenRecent = HashSet<Long>(16)
        val recentSongs = ArrayList<Song>(15)
        for (entry in history) {
            if (seenRecent.add(entry.songId)) {
                val song = songMap[entry.songId]
                if (song != null) {
                    recentSongs.add(song)
                    if (recentSongs.size >= 15) break
                }
            }
        }

        val topSongs = if (profile.playCountBySongId.isEmpty()) {
            emptyList()
        } else {
            profile.playCountBySongId.entries
                .sortedByDescending { it.value }
                .asSequence()
                .mapNotNull { songMap[it.key] }
                .take(10)
                .toList()
        }

        val dailyMixes = buildDailyMixesInternal(allSongs, profile)
        val madeForYou = buildMadeForYouInternal(allSongs, history, profile, now)
        val becauseYouListened = buildBecauseYouListenedToInternal(allSongs, songMap, history)

        return RecommendationSnapshot(
            recentSongs = recentSongs,
            topSongs = topSongs,
            dailyMixes = dailyMixes,
            madeForYou = madeForYou,
            becauseYouListened = becauseYouListened
        )
    }

    private fun buildAffinityProfile(
        allSongs: List<Song>,
        songMap: Map<Long, Song>,
        history: List<PlayHistoryEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): UserAffinityProfile {
        val playCountBySongId = HashMap<Long, Int>()
        val lastPlayedBySongId = HashMap<Long, Long>()
        val artistScore = HashMap<String, Double>()
        val genreScore = HashMap<String, Double>()

        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L

        for (entry in history) {
            playCountBySongId[entry.songId] = (playCountBySongId[entry.songId] ?: 0) + 1
            val prevLast = lastPlayedBySongId[entry.songId] ?: 0L
            if (entry.playedAt > prevLast) {
                lastPlayedBySongId[entry.songId] = entry.playedAt
            }

            val song = songMap[entry.songId] ?: continue
            val ageMs = (nowMs - entry.playedAt).coerceAtLeast(0L)
            val recencyWeight = 1.0 + (1.0 - (ageMs.toDouble() / thirtyDaysMs).coerceIn(0.0, 1.0))
            val completionWeight = (entry.completionPercent.coerceIn(10, 100) / 100.0)
            val score = recencyWeight * completionWeight

            artistScore[song.artist] = (artistScore[song.artist] ?: 0.0) + score
            val genre = song.genre
            if (!genre.isNullOrBlank()) {
                genreScore[genre] = (genreScore[genre] ?: 0.0) + score
            }
        }

        for (song in allSongs) {
            if (song.isFavorite) {
                artistScore[song.artist] = (artistScore[song.artist] ?: 0.0) + 3.0
                val genre = song.genre
                if (!genre.isNullOrBlank()) {
                    genreScore[genre] = (genreScore[genre] ?: 0.0) + 2.5
                }
            }
        }

        return UserAffinityProfile(
            playCountBySongId = playCountBySongId,
            lastPlayedBySongId = lastPlayedBySongId,
            artistScore = artistScore,
            genreScore = genreScore
        )
    }

    private fun scoreSong(
        song: Song,
        profile: UserAffinityProfile,
        favorDiscovery: Boolean = false
    ): Double {
        val plays = profile.playCountBySongId[song.id] ?: 0
        val favBoost = if (song.isFavorite) 6.0 else 0.0
        val artistAffinity = (profile.artistScore[song.artist] ?: 0.0) * 1.4
        val genreAffinity = song.genre?.let { profile.genreScore[it] ?: 0.0 } ?: 0.0
        val playScore = if (favorDiscovery) {
            if (plays == 0) 5.5 else 2.0 / (plays + 1)
        } else {
            plays * 2.2
        }
        return favBoost + artistAffinity + genreAffinity + playScore
    }

    private fun buildDailyMixesInternal(
        allSongs: List<Song>,
        profile: UserAffinityProfile
    ): List<SmartPlaylist> {
        if (allSongs.isEmpty()) return emptyList()

        val mixes = mutableListOf<SmartPlaylist>()
        val usedSongIds = HashSet<Long>()

        val validGenres = allSongs
            .filter { !it.genre.isNullOrBlank() }
            .groupBy { it.genre!!.trim() }
            .filter { (_, list) -> list.size >= 3 }
            .entries
            .sortedByDescending { (genre, list) ->
                (profile.genreScore[genre] ?: 0.0) * 10.0 + list.size
            }

        var mixNumber = 1
        for ((genre, genreSongs) in validGenres) {
            if (mixNumber > 4) break
            val ranked = genreSongs
                .sortedByDescending { song ->
                    val plays = profile.playCountBySongId[song.id] ?: 0
                    val discoveryBalance = if (plays == 0) 2.5 else plays * 1.5
                    scoreSong(song, profile) + discoveryBalance
                }
                .take(25)

            if (ranked.isNotEmpty()) {
                val artistsPreview = ranked.asSequence().map { it.artist }.distinct().take(3).joinToString(", ")
                mixes.add(
                    SmartPlaylist(
                        id = "daily_mix_$mixNumber",
                        title = "Daily Mix $mixNumber",
                        subtitle = artistsPreview,
                        description = "Un mix di $genre selezionato su misura per te: $artistsPreview e altro.",
                        badgeText = "DAILY MIX 0$mixNumber",
                        accentColor = mixColors[(mixNumber - 1) % mixColors.size],
                        songs = ranked
                    )
                )
                for (song in ranked) usedSongIds.add(song.id)
                mixNumber++
            }
        }

        if (mixNumber <= 4) {
            val remainingSongs = allSongs.filter { it.id !in usedSongIds }
            val byArtist = (if (remainingSongs.size >= 4) remainingSongs else allSongs)
                .groupBy { it.artist }
                .entries
                .sortedByDescending { (artist, list) ->
                    (profile.artistScore[artist] ?: 0.0) * 10.0 + list.size
                }

            val chunkCount = (4 - mixNumber + 1).coerceAtLeast(1)
            val chunkSize = (byArtist.size.coerceAtLeast(1) + chunkCount - 1) / chunkCount
            val artistGroups = byArtist.chunked(chunkSize)

            for (group in artistGroups) {
                if (mixNumber > 4) break
                val clusterSongs = group.asSequence()
                    .flatMap { it.value.asSequence() }
                    .distinctBy { it.id }
                    .sortedByDescending { scoreSong(it, profile) }
                    .take(25)
                    .toList()

                if (clusterSongs.isNotEmpty()) {
                    val artistsPreview = clusterSongs.asSequence().map { it.artist }.distinct().take(3).joinToString(", ")
                    mixes.add(
                        SmartPlaylist(
                            id = "daily_mix_$mixNumber",
                            title = "Daily Mix $mixNumber",
                            subtitle = artistsPreview,
                            description = "Mix creato per te con $artistsPreview e brani della tua libreria.",
                            badgeText = "DAILY MIX 0$mixNumber",
                            accentColor = mixColors[(mixNumber - 1) % mixColors.size],
                            songs = clusterSongs
                        )
                    )
                    mixNumber++
                }
            }
        }

        return mixes
    }

    private fun buildMadeForYouInternal(
        allSongs: List<Song>,
        history: List<PlayHistoryEntity>,
        profile: UserAffinityProfile,
        now: Long
    ): List<SmartPlaylist> {
        if (allSongs.isEmpty()) return emptyList()

        val thirtyDaysAgo = now - 30L * 24L * 60L * 60L * 1000L
        val result = mutableListOf<SmartPlaylist>()

        // 1. In ripetizione
        val recentCounts = HashMap<Long, Int>()
        for (entry in history) {
            if (entry.playedAt >= thirtyDaysAgo) {
                recentCounts[entry.songId] = (recentCounts[entry.songId] ?: 0) + 1
            }
        }

        val onRepeatSongs = allSongs
            .asSequence()
            .filter { (recentCounts[it.id] ?: 0) > 0 || (profile.playCountBySongId[it.id] ?: 0) > 0 || it.isFavorite }
            .sortedWith(
                compareByDescending<Song> { recentCounts[it.id] ?: 0 }
                    .thenByDescending { profile.playCountBySongId[it.id] ?: 0 }
                    .thenByDescending { if (it.isFavorite) 1 else 0 }
            )
            .take(25)
            .toList()
            .ifEmpty { allSongs.take(15) }

        result.add(
            SmartPlaylist(
                id = "on_repeat",
                title = "In ripetizione",
                subtitle = "I brani che stai ascoltando di più",
                description = "Le tracce che hai messo in loop negli ultimi 30 giorni.",
                badgeText = "ON REPEAT",
                accentColor = 0xFF148A08L,
                songs = onRepeatSongs
            )
        )

        // 2. Gemme nascoste
        val hiddenGems = allSongs
            .asSequence()
            .filter { (profile.playCountBySongId[it.id] ?: 0) <= 1 }
            .sortedByDescending { song -> scoreSong(song, profile, favorDiscovery = true) }
            .take(25)
            .toList()
            .ifEmpty { allSongs.reversed().take(20) }

        result.add(
            SmartPlaylist(
                id = "hidden_gems",
                title = "Gemme nascoste",
                subtitle = "Brani poco ascoltati in base ai tuoi gusti",
                description = "Tracce della tua libreria affini ai tuoi artisti e generi preferiti.",
                badgeText = "SCOPERTE",
                accentColor = 0xFF7358FFL,
                songs = hiddenGems
            )
        )

        // 3. Torna ad ascoltare
        val rewindSongs = allSongs
            .sortedWith(
                compareByDescending<Song> {
                    val lastPlayed = profile.lastPlayedBySongId[it.id] ?: Long.MAX_VALUE
                    if (lastPlayed == Long.MAX_VALUE) 0L else (now - lastPlayed)
                }.thenByDescending { profile.playCountBySongId[it.id] ?: 0 }
            )
            .take(25)

        result.add(
            SmartPlaylist(
                id = "repeat_rewind",
                title = "Torna ad ascoltare",
                subtitle = "I tuoi classici passati da riscoprire",
                description = "Brani che hai apprezzato in passato e che meritano un altro ascolto.",
                badgeText = "REWIND",
                accentColor = 0xFF0D73ECL,
                songs = rewindSongs
            )
        )

        // 4. Time Capsule / Sessione Lunga
        val songsWithYear = allSongs.filter { (it.year ?: 0) > 0 }
        val flashbackSongs = if (songsWithYear.isNotEmpty()) {
            songsWithYear.sortedBy { it.year }.take(25)
        } else {
            allSongs.sortedByDescending { it.duration }.take(25)
        }

        result.add(
            SmartPlaylist(
                id = "flashback_mix",
                title = if (songsWithYear.isNotEmpty()) "Time Capsule" else "Sessione Lunga",
                subtitle = if (songsWithYear.isNotEmpty()) {
                    "Un tuffo nel passato dai tuoi archivi"
                } else {
                    "I brani perfetti per un ascolto continuo"
                },
                description = "Selezione curata automaticamente dalla tua collezione locale.",
                badgeText = "CAPSULE",
                accentColor = 0xFFE8115BL,
                songs = flashbackSongs
            )
        )

        return result
    }

    private fun buildBecauseYouListenedToInternal(
        allSongs: List<Song>,
        songMap: Map<Long, Song>,
        history: List<PlayHistoryEntity>
    ): Pair<String, List<Song>>? {
        if (allSongs.isEmpty()) return null

        val targetArtist = history
            .asSequence()
            .mapNotNull { songMap[it.songId]?.artist }
            .firstOrNull { it != "Artista sconosciuto" }
            ?: allSongs.firstOrNull { it.isFavorite }?.artist
            ?: allSongs.groupBy { it.artist }.maxByOrNull { it.value.size }?.key
            ?: return null

        val artistGenres = allSongs
            .asSequence()
            .filter { it.artist == targetArtist }
            .mapNotNull { it.genre?.takeIf { g -> g.isNotBlank() } }
            .toSet()

        val recommendations = allSongs
            .asSequence()
            .filter { song ->
                song.artist == targetArtist ||
                    (artistGenres.isNotEmpty() && song.genre in artistGenres)
            }
            .sortedWith(
                compareByDescending<Song> { if (it.artist == targetArtist) 1 else 0 }
                    .thenByDescending { if (it.isFavorite) 1 else 0 }
            )
            .take(15)
            .toList()

        if (recommendations.isEmpty()) return null
        return targetArtist to recommendations
    }

    fun buildSongRadio(
        seed: Song,
        allSongs: List<Song>,
        history: List<PlayHistoryEntity>
    ): List<Song> {
        if (allSongs.isEmpty()) return listOf(seed)
        val songMap = allSongs.associateBy { it.id }
        val profile = buildAffinityProfile(allSongs, songMap, history)

        val candidates = allSongs
            .asSequence()
            .filter { it.id != seed.id }
            .sortedByDescending { candidate ->
                var score = 0.0
                if (candidate.artist.equals(seed.artist, ignoreCase = true)) score += 12.0
                if (!seed.genre.isNullOrBlank() && candidate.genre.equals(seed.genre, ignoreCase = true)) {
                    score += 8.0
                }
                if (seed.albumId > 0L && candidate.albumId == seed.albumId) score += 3.0
                if (seed.year != null && candidate.year != null) {
                    val yearDiff = abs(seed.year - candidate.year)
                    score += (5.0 - yearDiff * 0.5).coerceAtLeast(0.0)
                }
                score += scoreSong(candidate, profile) * 0.4
                score
            }
            .take(24)
            .toList()

        return listOf(seed) + candidates
    }

    fun computeWrappedStats(
        allSongs: List<Song>,
        history: List<PlayHistoryEntity>,
        fromTimestamp: Long,
        toTimestamp: Long = Long.MAX_VALUE
    ): WrappedStats {
        val songMap = allSongs.associateBy { it.id }
        val effectivePlays = history
            .asSequence()
            .filter { it.playedAt in fromTimestamp..toTimestamp }
            .mapNotNull { entry ->
                val song = songMap[entry.songId] ?: return@mapNotNull null
                song to entry.playedAt
            }
            .toList()

        val totalPlays = effectivePlays.size
        val totalDurationMs = effectivePlays.sumOf { (song, _) -> song.duration }
        val totalMinutes = (totalDurationMs / 60_000L).coerceAtLeast(
            if (totalPlays > 0) 1L else 0L
        )

        val uniqueSongsExplored = effectivePlays.asSequence().map { it.first.id }.distinct().count()
        val uniqueArtistsExplored = effectivePlays.asSequence().map { it.first.artist }.distinct().count()
        val favoriteSongsCount = allSongs.count { it.isFavorite }

        val songPlayCounts = effectivePlays.groupingBy { it.first.id }.eachCount()
        val topSongs = if (songPlayCounts.isNotEmpty()) {
            songPlayCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .mapNotNull { (songId, count) ->
                    val song = songMap[songId] ?: return@mapNotNull null
                    SongStat(
                        song = song,
                        playCount = count,
                        minutesListened = ((song.duration * count) / 60_000L).coerceAtLeast(1L)
                    )
                }
        } else {
            allSongs
                .sortedByDescending { if (it.isFavorite) 1 else 0 }
                .take(5)
                .map { song ->
                    SongStat(song = song, playCount = 0, minutesListened = 0L)
                }
        }

        val artistPlayCounts = effectivePlays.groupingBy { it.first.artist }.eachCount()
        val topArtists = if (artistPlayCounts.isNotEmpty()) {
            artistPlayCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { (artistName, count) ->
                    val artistPlays = effectivePlays.filter { it.first.artist == artistName }
                    val minutes = (artistPlays.sumOf { it.first.duration } / 60_000L).coerceAtLeast(1L)
                    val topSong = artistPlays
                        .groupingBy { it.first }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key
                    val libraryArtistSongs = allSongs.filter { it.artist == artistName }
                    ArtistStat(
                        name = artistName,
                        playCount = count,
                        minutesListened = minutes,
                        songCount = libraryArtistSongs.size,
                        topSong = topSong ?: libraryArtistSongs.firstOrNull(),
                        artworkUri = topSong?.albumArtUri
                            ?: libraryArtistSongs.firstNotNullOfOrNull { it.albumArtUri }
                    )
                }
        } else {
            allSongs
                .groupBy { it.artist }
                .entries
                .sortedByDescending { it.value.size }
                .take(5)
                .map { (artistName, artistSongs) ->
                    ArtistStat(
                        name = artistName,
                        playCount = 0,
                        minutesListened = 0L,
                        songCount = artistSongs.size,
                        topSong = artistSongs.firstOrNull(),
                        artworkUri = artistSongs.firstNotNullOfOrNull { it.albumArtUri }
                    )
                }
        }

        val genreSource = if (effectivePlays.isNotEmpty()) {
            effectivePlays.mapNotNull { it.first.genre?.takeIf { g -> g.isNotBlank() } }
        } else {
            allSongs.mapNotNull { it.genre?.takeIf { g -> g.isNotBlank() } }
        }

        val genreCounts = genreSource.groupingBy { it }.eachCount()
        val totalGenreEntries = genreCounts.values.sum().coerceAtLeast(1)
        val topGenres = genreCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (genre, count) ->
                GenreStat(
                    name = genre,
                    playCount = count,
                    percentage = (count.toFloat() / totalGenreEntries.toFloat())
                )
            }

        val (peakTitle, peakSubtitle) = computePeakListeningPeriod(effectivePlays.map { it.second })
        val (personalityTitle, personalityDesc) = computePersonality(
            totalPlays = totalPlays,
            uniqueSongs = uniqueSongsExplored,
            uniqueArtists = uniqueArtistsExplored,
            favoriteCount = favoriteSongsCount,
            topArtistShare = if (totalPlays > 0 && topArtists.isNotEmpty()) {
                topArtists.first().playCount.toFloat() / totalPlays.toFloat()
            } else 0f
        )

        return WrappedStats(
            totalMinutes = totalMinutes,
            totalPlays = totalPlays,
            uniqueSongsExplored = uniqueSongsExplored,
            uniqueArtistsExplored = uniqueArtistsExplored,
            favoriteSongsCount = favoriteSongsCount,
            topArtists = topArtists,
            topSongs = topSongs,
            topGenres = topGenres,
            peakPeriodTitle = peakTitle,
            peakPeriodSubtitle = peakSubtitle,
            personalityTitle = personalityTitle,
            personalityDescription = personalityDesc
        )
    }

    private fun computePeakListeningPeriod(timestamps: List<Long>): Pair<String, String> {
        if (timestamps.isEmpty()) {
            return "Pronto per iniziare" to "Ascolta qualche brano per scoprire i tuoi orari preferiti"
        }

        var morning = 0
        var afternoon = 0
        var evening = 0
        var night = 0
        val cal = Calendar.getInstance()

        for (ts in timestamps) {
            cal.timeInMillis = ts
            when (cal.get(Calendar.HOUR_OF_DAY)) {
                in 6..11 -> morning++
                in 12..17 -> afternoon++
                in 18..22 -> evening++
                else -> night++
            }
        }

        return when (maxOf(morning, afternoon, evening, night)) {
            morning -> "Mattiniero Sonoro" to "Dai il meglio tra le 06:00 e le 12:00 ($morning ascolti)"
            afternoon -> "Ritmo Pomeridiano" to "La tua fascia d'oro è il pomeriggio ($afternoon ascolti)"
            evening -> "Aura Serale" to "Ami rilassarti con la musica di sera ($evening ascolti)"
            else -> "Nottambulo della Musica" to "Le tue sessioni migliori avengono di notte ($night ascolti)"
        }
    }

    private fun computePersonality(
        totalPlays: Int,
        uniqueSongs: Int,
        uniqueArtists: Int,
        favoriteCount: Int,
        topArtistShare: Float
    ): Pair<String, String> {
        return when {
            totalPlays == 0 ->
                "Nuovo Viaggiatore" to "Nessun ascolto registrato in questo periodo: scopri la tua libreria!"
            topArtistShare >= 0.45f ->
                "Fan Devoto" to "Quando trovi l'artista giusto, non lo molli più."
            uniqueArtists >= 12 && uniqueSongs >= 20 ->
                "Esploratore Sonoro" to "Spazi continuamente tra generi, album e nuovi artisti."
            totalPlays > uniqueSongs * 2 ->
                "Collezionista di Hit" to "Ami mandare in loop i tuoi brani del cuore."
            favoriteCount >= 10 ->
                "Curatore Selettivo" to "Sai esattamente cosa ami e costruisci una collezione impeccabile."
            else ->
                "Audiofilo Equilibrato" to "Alterni con naturalezza grandi classici e nuove scoperte."
        }
    }
}
