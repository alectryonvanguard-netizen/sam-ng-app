package com.example.twirlly

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class Comment(
    val user: String, val text: String, val id: Long = 0L, val time: Long = 0L,
    val likes: Int = 0, val liked: Boolean = false
)

fun wordCount(s: String): Int = s.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

data class Video(
    val id: Long,
    val url: String,
    val isImage: Boolean = false,
    val local: Boolean = false,
    val user: String,
    val caption: String,
    val music: String,
    val likes: Int,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val shares: Int = 0,
    val comments: List<Comment> = emptyList(),
    val createdAt: Long = 0L,
    val images: List<String> = emptyList(),
    val audio: String? = null,
    val project: String? = null,
    val seriesTitle: String? = null,
    val seriesId: Long? = null,
    val episodeNo: Int = 0,
    val story: Boolean = false
)

data class Draft(val id: Long, val title: String, val json: String, val savedAt: Long)

/** How a new post is added: as a single post, as a new drama, or as the next episode of one of your dramas. */
class SeriesChoice(val newTitle: String? = null, val addToId: Long? = null, val splitClips: Boolean = false, val story: Boolean = false)

const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/** Whole days left before a name or username can change again (0 = can change now). */
fun cooldownDaysLeft(changedAt: Long): Int {
    if (changedAt <= 0L) return 0
    val left = WEEK_MS - (System.currentTimeMillis() - changedAt)
    return if (left <= 0L) 0 else ((left + 86_399_999L) / 86_400_000L).toInt()
}

data class Profile(
    val name: String = "creator",
    val displayName: String = "",
    val bio: String = "Hello! I'm new on Twirlly.",
    val avatar: String? = null,
    val banner: String? = null,
    val bannerVideo: Boolean = false,
    val bannerStartMs: Long = 0L,
    val bannerScale: Float = 1f,
    val bannerOx: Float = 0f,
    val bannerOy: Float = 0f,
    val verified: Boolean = false,
    val verifyRequest: String = "",
    val nameChangedAt: Long = 0L,
    val usernameChangedAt: Long = 0L,
    val isPrivate: Boolean = false
)

const val MAX_OWN_POSTS = 100
const val STORAGE_LIMIT = 5_000_000L

data class AppSettings(
    val email: String = "",
    val breakHours: Int = 2,
    val kidsMode: Boolean = false,
    val pinHash: String = "",
    val bedtimeOn: Boolean = false,
    val bedStart: Int = 22,
    val bedEnd: Int = 6,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastDay: Long = 0L,
    val coins: Int = 0,
    val notifications: Boolean = true,
    val autoplay: Boolean = true,
    val muted: Boolean = false,
    val theme: Int = 0,
    val accent: Int = 0,
    val onboarded: Boolean = false,
    val premium: Boolean = false
)

data class ActivityEvent(val id: Long, val text: String, val time: Long, val type: Int = 0, val videoId: Long = 0L, val sys: Boolean = true)

private fun Video.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("url", url); put("img", isImage); put("local", local)
    put("user", user); put("cap", caption); put("music", music); put("likes", likes)
    put("liked", liked); put("saved", saved); put("shares", shares); put("t", createdAt)
    put("comments", JSONArray().also { arr ->
        comments.forEach {
            arr.put(JSONObject().put("u", it.user).put("x", it.text).put("i", it.id).put("t", it.time).put("l", it.likes).put("k", it.liked))
        }
    })
    put("images", JSONArray(images)); put("audio", audio ?: ""); put("project", project ?: "")
    put("ser", seriesTitle ?: ""); put("sid", seriesId ?: 0L); put("epn", episodeNo); put("story", story)
}

private fun JSONObject.toVideo(): Video {
    val cs = optJSONArray("comments") ?: JSONArray()
    val imgs = optJSONArray("images") ?: JSONArray()
    return Video(
        id = getLong("id"), url = getString("url"), isImage = optBoolean("img"), local = optBoolean("local"),
        user = getString("user"), caption = getString("cap"), music = optString("music"), likes = optInt("likes"),
        liked = optBoolean("liked"), saved = optBoolean("saved"), shares = optInt("shares"),
        comments = (0 until cs.length()).map {
            val o = cs.getJSONObject(it)
            Comment(o.getString("u"), o.getString("x"), o.optLong("i"), o.optLong("t"), o.optInt("l"), o.optBoolean("k"))
        },
        createdAt = optLong("t"),
        images = (0 until imgs.length()).map { imgs.getString(it) },
        audio = optString("audio", "").takeIf { it.isNotBlank() },
        project = optString("project", "").takeIf { it.isNotBlank() },
        seriesTitle = optString("ser", "").takeIf { it.isNotBlank() },
        seriesId = optLong("sid", 0L).takeIf { it != 0L },
        episodeNo = optInt("epn", 0),
        story = optBoolean("story", false)
    )
}

/** All data of the app. Everything is stored on the phone and every screen reads from here, so a change shows up everywhere. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val stateFile = File(ctx.filesDir, "twirlly_state.json")
    private val writer = Executors.newSingleThreadExecutor()

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()
    private val _following = MutableStateFlow<Set<String>>(emptySet())
    val following: StateFlow<Set<String>> = _following.asStateFlow()
    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private val _events = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val events: StateFlow<List<ActivityEvent>> = _events.asStateFlow()
    private val _drafts = MutableStateFlow<List<Draft>>(emptyList())
    val drafts: StateFlow<List<Draft>> = _drafts.asStateFlow()
    private val _playlists = MutableStateFlow<Map<String, List<Long>>>(emptyMap())
    val playlists: StateFlow<Map<String, List<Long>>> = _playlists.asStateFlow()
    private val lastEp = mutableMapOf<Long, Int>()
    private val _hidden = MutableStateFlow<Set<Long>>(emptySet())
    /** Posts you marked "Not interested". */
    val hidden: StateFlow<Set<Long>> = _hidden.asStateFlow()
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    /** Creators you blocked. Their posts are not shown. */
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()
    private val reports = mutableListOf<JSONObject>()
    private val _landscape = MutableStateFlow(false)
    val landscape: StateFlow<Boolean> = _landscape.asStateFlow()
    fun setLandscape(on: Boolean) { _landscape.value = on }
    private val watchLog = mutableMapOf<String, Long>()
    private val _breakKind = MutableStateFlow("break")
    /** "break" (watched for a long time) or "bedtime". */
    val breakKind: StateFlow<String> = _breakKind.asStateFlow()
    private var bedSnoozeUntil = 0L
    private var watchedSecs = 0L
    private var lastTickAt = System.currentTimeMillis()
    private val _breakDue = MutableStateFlow(false)
    /** True when it is time for a break (you have been watching for the chosen number of hours). */
    val breakDue: StateFlow<Boolean> = _breakDue.asStateFlow()

    private fun dayKey(ms: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))

    private fun inBedtime(start: Int, end: Int): Boolean {
        if (start == end) return false
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (start < end) h in start until end else (h >= start || h < end)
    }

    private fun touchDay(now: Long) {
        val today = (now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L
        val st = _settings.value
        if (st.lastDay == today) return
        val streak = if (st.lastDay == today - 1) st.streak + 1 else 1
        _settings.update { it.copy(lastDay = today, streak = streak, bestStreak = maxOf(it.bestStreak, streak)) }
        persist()
    }

    /** Called once a second by the app. Time spent watching adds up; being away for 30 minutes counts as a break. */
    fun watchTick() {
        if (!AppState.foreground || !_settings.value.onboarded || _breakDue.value) return
        val now = System.currentTimeMillis()
        if (now - lastTickAt > 30 * 60 * 1000L) watchedSecs = 0L
        lastTickAt = now
        touchDay(now)
        val key = dayKey(now)
        watchLog[key] = (watchLog[key] ?: 0L) + 1L
        watchedSecs += 1
        if (watchedSecs % 30L == 0L) persist()
        if (watchedSecs % 60L == 0L) purgeStories()
        val st = _settings.value
        if (st.bedtimeOn && now >= bedSnoozeUntil && inBedtime(st.bedStart, st.bedEnd)) {
            _breakKind.value = "bedtime"
            _breakDue.value = true
            return
        }
        if (st.breakHours > 0 && watchedSecs >= st.breakHours * 3600L) {
            _breakKind.value = "break"
            _breakDue.value = true
        }
    }

    fun watchLogMap(): Map<String, Long> = watchLog.toMap()

    fun endBreak() {
        if (_breakKind.value == "bedtime") bedSnoozeUntil = System.currentTimeMillis() + 30 * 60 * 1000L
        watchedSecs = 0L
        lastTickAt = System.currentTimeMillis()
        _breakDue.value = false
        persist()
    }

    fun previewBreak(kind: String = "break") { _breakKind.value = kind; _breakDue.value = true }

    private val _drama = MutableStateFlow<Pair<Long, Int>?>(null)
    /** The drama that is open (series id and the episode to start on), or null. */
    val drama: StateFlow<Pair<Long, Int>?> = _drama.asStateFlow()
    fun openDrama(seriesId: Long, episode: Int) { _drama.value = Pair(seriesId, episode) }
    fun closeDrama() { _drama.value = null }

    init {
        load()
        purgeStories()
        ensureDemo()
    }

    private fun ensureDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            val demo = _videos.value.filter { !it.local }
            if (demo.isEmpty() || demo.any { !File(it.url).exists() }) {
                val fresh = DemoContent.build(ctx).map { f ->
                    demo.firstOrNull { it.id == f.id }?.let { o ->
                        f.copy(liked = o.liked, saved = o.saved, shares = o.shares, likes = o.likes, comments = o.comments)
                    } ?: f
                }
                _videos.update { cur -> fresh + cur.filter { it.local } }
                persist()
            }
        }
    }

    private fun load() {
        try {
            if (!stateFile.exists()) { addEvent("Welcome to Twirlly"); return }
            val o = JSONObject(stateFile.readText())
            val arr = o.getJSONArray("videos")
            _videos.value = (0 until arr.length()).map { arr.getJSONObject(it).toVideo() }
                .filter { !it.local || File(it.url).exists() || it.project != null }
            val f = o.optJSONArray("following") ?: JSONArray()
            _following.value = (0 until f.length()).map { f.getString(it) }.toSet()
            o.optJSONObject("profile")?.let { p ->
                _profile.value = Profile(
                    name = p.optString("name", "creator"), displayName = p.optString("dn", ""),
                    bio = p.optString("bio", ""),
                    avatar = p.optString("avatar", "").takeIf { it.isNotBlank() },
                    banner = p.optString("banner", "").takeIf { it.isNotBlank() },
                    bannerVideo = p.optBoolean("bv"),
                    bannerStartMs = p.optLong("bs", 0L),
                    bannerScale = p.optDouble("bsc", 1.0).toFloat(),
                    bannerOx = p.optDouble("box", 0.0).toFloat(),
                    bannerOy = p.optDouble("boy", 0.0).toFloat(),
                    verified = p.optBoolean("ver", false),
                    verifyRequest = p.optString("vreq", ""),
                    nameChangedAt = p.optLong("nca", 0L),
                    usernameChangedAt = p.optLong("uca", 0L),
                    isPrivate = p.optBoolean("private")
                )
            }
            o.optJSONObject("settings")?.let { s ->
                _settings.value = AppSettings(
                    notifications = s.optBoolean("notif", true), autoplay = s.optBoolean("auto", true),
                    muted = s.optBoolean("muted", false), theme = s.optInt("theme", 0), accent = s.optInt("accent", 0),
                    onboarded = s.optBoolean("onb", false), premium = s.optBoolean("prem", false), email = s.optString("email", ""), coins = s.optInt("coins", 0), breakHours = s.optInt("brk", 2), kidsMode = s.optBoolean("kids", false), pinHash = s.optString("pin", ""), bedtimeOn = s.optBoolean("bed", false), bedStart = s.optInt("bedS", 22), bedEnd = s.optInt("bedE", 6), streak = s.optInt("streak", 0), bestStreak = s.optInt("best", 0), lastDay = s.optLong("lday", 0L)
                )
            }
            watchedSecs = o.optLong("watch", 0L)
            o.optJSONArray("drafts")?.let { a ->
                _drafts.value = (0 until a.length()).map { i -> a.getJSONObject(i).let { d -> Draft(d.getLong("id"), d.optString("t", "Draft"), d.getString("j"), d.optLong("at")) } }
            }
            o.optJSONObject("playlists")?.let { po ->
                val m = mutableMapOf<String, List<Long>>()
                po.keys().forEach { k -> val a = po.getJSONArray(k); m[k] = (0 until a.length()).map { a.getLong(it) } }
                _playlists.value = m
            }
            _hidden.value = (o.optJSONArray("hidden") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() }
            _blocked.value = (o.optJSONArray("blocked") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            reports.clear()
            (o.optJSONArray("reports") ?: JSONArray()).let { a -> for (i in 0 until a.length()) reports.add(a.getJSONObject(i)) }
            lastEp.clear()
            o.optJSONObject("lastep")?.let { lo -> lo.keys().forEach { k -> lastEp[k.toLong()] = lo.getInt(k) } }
            watchLog.clear()
            o.optJSONObject("wlog")?.let { wo -> wo.keys().forEach { k -> watchLog[k] = wo.getLong(k) } }
            val ev = o.optJSONArray("events") ?: JSONArray()
            _events.value = (0 until ev.length()).map {
                val e = ev.getJSONObject(it)
                ActivityEvent(e.getLong("id"), e.getString("x"), e.getLong("t"), e.optInt("ty"), e.optLong("vid"), e.optBoolean("sys", true))
            }
        } catch (e: Exception) {
            _videos.value = emptyList()
        }
    }

    private fun buildJson(): JSONObject = JSONObject().apply {
        put("videos", JSONArray().also { a -> _videos.value.forEach { a.put(it.toJson()) } })
        put("following", JSONArray(_following.value.toList()))
        val p = _profile.value
        put("profile", JSONObject().put("name", p.name).put("dn", p.displayName).put("bio", p.bio)
            .put("avatar", p.avatar ?: "").put("banner", p.banner ?: "").put("bv", p.bannerVideo).put("bs", p.bannerStartMs).put("bsc", p.bannerScale.toDouble()).put("box", p.bannerOx.toDouble()).put("boy", p.bannerOy.toDouble()).put("ver", p.verified).put("vreq", p.verifyRequest).put("nca", p.nameChangedAt).put("uca", p.usernameChangedAt).put("private", p.isPrivate))
        val s = _settings.value
        put("settings", JSONObject().put("notif", s.notifications).put("auto", s.autoplay).put("muted", s.muted)
            .put("theme", s.theme).put("accent", s.accent).put("onb", s.onboarded).put("prem", s.premium).put("email", s.email).put("coins", s.coins).put("brk", s.breakHours).put("kids", s.kidsMode).put("pin", s.pinHash).put("bed", s.bedtimeOn).put("bedS", s.bedStart).put("bedE", s.bedEnd).put("streak", s.streak).put("best", s.bestStreak).put("lday", s.lastDay))
        put("watch", watchedSecs)
        put("drafts", JSONArray().also { a -> _drafts.value.forEach { a.put(JSONObject().put("id", it.id).put("t", it.title).put("j", it.json).put("at", it.savedAt)) } })
        put("playlists", JSONObject().also { po -> _playlists.value.forEach { (k, v) -> po.put(k, JSONArray(v)) } })
        put("hidden", JSONArray(_hidden.value.toList()))
        put("blocked", JSONArray(_blocked.value.toList()))
        put("reports", JSONArray().also { a -> reports.takeLast(50).forEach { a.put(it) } })
        put("lastep", JSONObject().also { lo -> lastEp.forEach { (k, v) -> lo.put(k.toString(), v) } })
        put("wlog", JSONObject().also { wo -> watchLog.entries.sortedByDescending { it.key }.take(14).forEach { wo.put(it.key, it.value) } })
        put("events", JSONArray().also { a ->
            _events.value.forEach { a.put(JSONObject().put("id", it.id).put("x", it.text).put("t", it.time).put("ty", it.type).put("vid", it.videoId).put("sys", it.sys)) }
        })
    }

    fun exportJson(): String = buildJson().toString(2)

    private fun persist() {
        val json = buildJson().toString()
        writer.execute { try { stateFile.writeText(json) } catch (_: Exception) {} }
    }

    private fun addEvent(text: String, type: Int = 0, videoId: Long = 0L, sys: Boolean = true) {
        _events.update { listOf(ActivityEvent(System.nanoTime(), text, System.currentTimeMillis(), type, videoId, sys)) + it.take(79) }
    }

    private fun toast(msg: String) {
        viewModelScope.launch(Dispatchers.Main) { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun mutate(id: Long, f: (Video) -> Video) {
        _videos.update { l -> l.map { if (it.id == id) f(it) else it } }
        persist()
    }

    fun toggleLike(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        mutate(id) { it.copy(liked = !it.liked, likes = it.likes + if (it.liked) -1 else 1) }
        if (!v.liked) addEvent("You liked @${v.user}'s post", 1, id)
    }

    fun doubleTapLike(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        if (v.liked) return
        mutate(id) { it.copy(liked = true, likes = it.likes + 1) }
        addEvent("You liked @${v.user}'s post", 1, id)
    }

    fun toggleSave(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        mutate(id) { it.copy(saved = !it.saved) }
        if (!v.saved) addEvent("You saved @${v.user}'s post", 3, id)
    }

    fun share(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        mutate(id) { it.copy(shares = it.shares + 1) }
        addEvent("You shared @${v.user}'s post", 4, id)
    }

    fun addComment(id: Long, text: String) {
        if (text.isBlank() || wordCount(text) > 500) return
        val me = _profile.value.name
        val c = Comment(me, text.trim(), System.nanoTime(), System.currentTimeMillis())
        val owner = _videos.value.firstOrNull { it.id == id }?.user ?: ""
        mutate(id) { it.copy(comments = it.comments + c) }
        addEvent("You commented on @$owner's post: " + c.text.take(60), 2, id)
    }

    fun deleteComment(id: Long, index: Int) = mutate(id) { v ->
        if (index in v.comments.indices) v.copy(comments = v.comments.filterIndexed { i, _ -> i != index }) else v
    }

    fun toggleCommentLike(id: Long, index: Int) = mutate(id) { v ->
        if (index !in v.comments.indices) v
        else v.copy(comments = v.comments.mapIndexed { i, c ->
            if (i == index) c.copy(liked = !c.liked, likes = c.likes + if (c.liked) -1 else 1) else c
        })
    }

    fun toggleFollow(user: String) {
        val now = user !in _following.value
        _following.update { if (user in it) it - user else it + user }
        addEvent(if (now) "You followed @$user" else "You unfollowed @$user", 5)
        persist()
    }

    fun postProject(p: Project, caption: String, series: SeriesChoice? = null, onDone: () -> Unit) {
        if (p.clips.isEmpty()) return
        val me = _profile.value.name
        val now = System.currentTimeMillis()
        val asStory = series?.story == true
        val target = if (asStory) null else series?.addToId?.let { id -> _videos.value.firstOrNull { it.id == id && it.local } }
        val title = if (asStory) null else (target?.seriesTitle ?: series?.newTitle?.trim()?.takeIf { it.isNotEmpty() }?.take(60))
        val sid: Long? = if (title == null) null else (target?.seriesId ?: target?.id ?: now)
        val firstNo = if (sid != null && target != null) _videos.value.count { it.seriesId == sid } + 1 else 1
        // many episodes at once: every clip becomes its own episode
        val parts: List<Project> =
            if (title != null && series?.splitClips == true && p.clips.size > 1)
                p.clips.map { c -> p.copy(clips = listOf(c.copy(trans = 0)), layers = emptyList(), fx = emptyList(), audios = emptyList()) }
            else listOf(p)
        val made = parts.mapIndexed { i, proj ->
            Video(
                id = now + i, url = proj.clips[0].path, isImage = false, local = true, user = me,
                caption = caption.ifBlank { if (title != null) "Episode " + (firstNo + i) else "My new edit" },
                music = proj.audios.firstOrNull()?.name?.ifBlank { "Added song" } ?: "Original sound - $me",
                likes = 0, createdAt = now + i, project = proj.toJson(),
                seriesTitle = title, seriesId = sid, episodeNo = if (sid != null) firstNo + i else 0,
                story = asStory
            )
        }
        // limited storage: only links to your gallery are kept, and only the newest posts
        _videos.update { cur ->
            val all = made.reversed() + cur
            val keep = all.filter { it.local }.take(MAX_OWN_POSTS).map { it.id }.toSet()
            all.filter { !it.local || it.id in keep }
        }
        addEvent(
            when {
                asStory -> "Your story was posted. It disappears in 24 hours"
                title == null -> "Your edit was posted"
                made.size > 1 -> made.size.toString() + " episodes of \"" + title + "\" were posted"
                target != null -> "Episode " + firstNo + " added to \"" + title + "\""
                else -> "Your drama \"" + title + "\" was posted"
            }, 6, made.first().id
        )
        persist()
        if (_settings.value.notifications) Notifier.send(ctx, "Posted on Twirlly", "Your edit is live on your profile.")
        onDone()
    }

    /** Stories disappear after 24 hours. */
    fun purgeStories() {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        if (_videos.value.any { it.story && it.createdAt < cutoff }) {
            _videos.update { l -> l.filter { !(it.story && it.createdAt < cutoff) } }
            persist()
        }
    }

    fun saveDraft(json: String, title: String) {
        val now = System.currentTimeMillis()
        _drafts.update { (listOf(Draft(now, title, json, now)) + it).take(10) }
        persist()
    }
    fun deleteDraft(id: Long) { _drafts.update { l -> l.filter { it.id != id } }; persist() }
    fun removeDraftByJson(json: String) { _drafts.update { l -> l.filter { it.json != json } }; persist() }

    fun createPlaylist(name: String) {
        val n = name.trim().take(30)
        if (n.isEmpty() || _playlists.value.containsKey(n)) return
        _playlists.update { it + (n to emptyList()) }
        persist()
    }
    fun addToPlaylist(name: String, id: Long) {
        _playlists.update { m -> m + (name to ((m[name] ?: emptyList()) + id).distinct()) }
        persist()
    }
    fun removeFromPlaylist(name: String, id: Long) {
        _playlists.update { m -> m + (name to (m[name] ?: emptyList()).filter { it != id }) }
        persist()
    }
    fun deletePlaylist(name: String) { _playlists.update { it - name }; persist() }

    fun notInterested(id: Long) { _hidden.update { it + id }; persist() }

    fun blockUser(user: String) {
        _blocked.update { it + user }
        _following.update { it - user }
        addEvent("You blocked @$user")
        persist()
    }
    fun unblockUser(user: String) { _blocked.update { it - user }; persist() }

    /** Saved on the phone. Replace this later to send reports to your own server. */
    fun reportPost(id: Long, reason: String) {
        val v = _videos.value.firstOrNull { it.id == id }
        reports.add(JSONObject().put("post", id).put("user", v?.user ?: "").put("reason", reason).put("at", System.currentTimeMillis()))
        _hidden.update { it + id }
        addEvent("You reported a post")
        persist()
    }

    fun lastEpisode(seriesId: Long): Int? = lastEp[seriesId]
    fun setLastEpisode(seriesId: Long, ep: Int) {
        if (lastEp[seriesId] != ep) { lastEp[seriesId] = ep; persist() }
    }

    private fun sha(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(("twirlly:" + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    fun setKids(pin: String) { _settings.update { it.copy(kidsMode = true, pinHash = sha(pin)) }; persist() }
    fun checkPin(pin: String): Boolean = sha(pin) == _settings.value.pinHash
    fun disableKids() { _settings.update { it.copy(kidsMode = false, pinHash = "") }; persist() }

    fun markVerificationRequested(json: String) {
        _profile.update { it.copy(verifyRequest = json) }
        addEvent("Verification request saved")
        persist()
    }

    /** Replaces everything with a backup file made by Settings > Back up. */
    fun importJson(text: String): Boolean = try {
        JSONObject(text)
        stateFile.writeText(text)
        load()
        purgeStories()
        ensureDemo()
        true
    } catch (e: Exception) { false }

    fun deletePost(id: Long) {
        val v = _videos.value.firstOrNull { it.id == id } ?: return
        // deleting the first episode removes the whole drama
        _videos.update { l -> l.filter { it.id != id && !(v.seriesId == v.id && it.seriesId == v.id) } }
        addEvent("Post deleted")
        persist()
    }

    /** Name and username. The username is unique (it cannot match a creator in the app) and changes everywhere at once. */
    fun saveProfile(displayName: String, username: String, bio: String, isPrivate: Boolean, onboarding: Boolean = false): String? {
        val n = username.trim().lowercase()
        if (!Regex("^[a-z0-9_.]{3,24}$").matches(n)) return "Username must be 3 to 24 letters, numbers, _ or ."
        if (DemoContent.creators.any { it.name == n }) return "That username is already taken."
        val old = _profile.value
        val newDisplay = displayName.trim().take(40)
        val nameChanged = newDisplay != old.displayName
        val userChanged = n != old.name
        if (!onboarding) {
            val nd = cooldownDaysLeft(old.nameChangedAt)
            val ud = cooldownDaysLeft(old.usernameChangedAt)
            if (nameChanged && nd > 0) return "You can change your name again in $nd day" + (if (nd == 1) "." else "s.")
            if (userChanged && ud > 0) return "You can change your username again in $ud day" + (if (ud == 1) "." else "s.")
        }
        val now = System.currentTimeMillis()
        _profile.value = old.copy(
            name = n, displayName = newDisplay, bio = bio.trim().take(200), isPrivate = isPrivate,
            nameChangedAt = if (nameChanged && !onboarding) now else old.nameChangedAt,
            usernameChangedAt = if (userChanged && !onboarding) now else old.usernameChangedAt
        )
        if (n != old.name) {
            _videos.update { l ->
                l.map { v ->
                    v.copy(
                        user = if (v.local) n else v.user,
                        music = if (v.local && v.music.startsWith("Original sound - ")) "Original sound - $n" else v.music,
                        comments = v.comments.map { c -> if (c.user == old.name) c.copy(user = n) else c }
                    )
                }
            }
        }
        addEvent("Profile updated")
        persist()
        return null
    }

    fun setImageBytes(bytes: ByteArray, banner: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(ctx.filesDir, "media")
                dir.mkdirs()
                val f = File(dir, (if (banner) "banner_" else "avatar_") + System.currentTimeMillis() + ".jpg")
                f.writeBytes(bytes)
                val old = if (banner) _profile.value.banner else _profile.value.avatar
                if (old != null && !old.startsWith("content:")) File(old).delete()
                _profile.update { if (banner) it.copy(banner = f.absolutePath, bannerVideo = false, bannerStartMs = 0L, bannerScale = 1f, bannerOx = 0f, bannerOy = 0f) else it.copy(avatar = f.absolutePath) }
                addEvent(if (banner) "Banner updated" else "Profile picture updated", 7)
                persist()
            } catch (e: Exception) {
                toast("Could not save the picture")
            }
        }
    }

    /** Banner video (5 to 10 seconds). Only the link to the gallery file is kept, nothing is copied. */
    fun setBannerVideo(uri: String, startMs: Long, scale: Float, ox: Float, oy: Float) {
        val old = _profile.value.banner
        if (old != null && !old.startsWith("content:")) File(old).delete()
        _profile.update { it.copy(banner = uri, bannerVideo = true, bannerStartMs = startMs, bannerScale = scale, bannerOx = ox, bannerOy = oy) }
        addEvent("Banner updated", 7)
        persist()
    }

    /** Test coins. Real purchases will be connected later. */
    fun addCoins(n: Int) {
        _settings.update { it.copy(coins = it.coins + n) }
        persist()
    }

    fun spendCoins(n: Int): Boolean {
        if (_settings.value.coins < n) return false
        _settings.update { it.copy(coins = it.coins - n) }
        persist()
        return true
    }

    fun updateSettings(f: (AppSettings) -> AppSettings) {
        _settings.update(f)
        persist()
    }

    fun setPremium(on: Boolean) {
        _settings.update { it.copy(premium = on) }
        if (on) addEvent("Welcome to Twirlly Premium", 8)
        persist()
    }

    fun completeOnboarding(displayName: String, username: String): String? {
        val err = saveProfile(displayName, username, _profile.value.bio, false, onboarding = true)
        if (err != null) return err
        _settings.update { it.copy(onboarded = true) }
        persist()
        return null
    }

    fun mediaBytes(): Long =
        File(ctx.filesDir, "media").walkTopDown().filter { it.isFile }.sumOf { it.length() } + (if (stateFile.exists()) stateFile.length() else 0L)

    fun clearTemp() {
        try { ctx.cacheDir.deleteRecursively() } catch (_: Exception) {}
        ensureDemo()
    }

    fun signIn(email: String) {
        _settings.update { it.copy(email = email.trim()) }
        persist()
    }

    fun signOut() {
        _settings.update { it.copy(email = "", onboarded = false) }
        persist()
    }
}
